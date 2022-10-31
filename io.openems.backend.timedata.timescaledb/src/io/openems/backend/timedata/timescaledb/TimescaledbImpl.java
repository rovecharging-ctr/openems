package io.openems.backend.timedata.timescaledb;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.postgresql.Driver;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.TreeBasedTable;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.backend.common.metadata.Metadata;
import io.openems.backend.common.timedata.Timedata;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.StringUtils;
import io.openems.common.utils.ThreadPoolUtils;

@Designate(ocd = Config.class, factory = false)
@Component(//
		name = "Timedata.TimescaleDB", //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		immediate = true //
)
public class TimescaledbImpl extends AbstractOpenemsBackendComponent implements Timescaledb, Timedata {

	private static final int POINTS_QUEUE_SIZE = 1_000_000;
	private static final int MAX_POINTS_PER_WRITE = 1_000;
	private static final int MAX_AGGREGATE_WAIT = 10; // [s]

	private final Logger log = LoggerFactory.getLogger(TimescaledbImpl.class);
	private final HikariDataSource dataSource;
	private final boolean isReadOnly;
	private final BlockingQueue<Point> pointsQueue = new ArrayBlockingQueue<>(POINTS_QUEUE_SIZE);

	private final ThreadPoolExecutor executor;
	private final ScheduledExecutorService debugLogExecutor = Executors.newSingleThreadScheduledExecutor();
	private final ExecutorService mergePointsExecutor = Executors.newSingleThreadExecutor();

	private Schema schema = null;

	@Activate
	public TimescaledbImpl(@Reference Metadata metadata, Config config) throws SQLException {
		super("Timedata.TimescaleDB");

		this.logInfo(this.log, "Activate [" //
				+ config.user() + (config.password() != null ? ":xxx" : "") //
				+ "@" + config.host() + ":" + config.port() //
				+ "/" + config.database() //
				+ (config.isReadOnly() ? "|READ_ONLY_MODE" : "") //
				+ "]");

		// Configuration
		this.dataSource = getDataSource(//
				config.host(), config.port(), config.database(), //
				config.user(), config.password());
		this.isReadOnly = config.isReadOnly();

		// Executors for debug, merge points and write to database
		this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(config.poolSize(),
				new ThreadFactoryBuilder().setNameFormat("TimescaleDB-%d").build());

		this.debugLogExecutor.scheduleWithFixedDelay(() -> {
			int pointsQueueSize = this.pointsQueue.size();
			this.log.info(new StringBuilder("[monitor] TimescaleDB ") //
					.append("Pool: ").append(this.executor.getPoolSize()).append(", ") //
					.append("Active: ").append(this.executor.getActiveCount()).append(", ") //
					.append("Pending: ").append(this.executor.getQueue().size()).append(", ") //
					.append("Completed: ").append(this.executor.getCompletedTaskCount()).append(", ") //
					.append("QueuedPoints: ").append(this.pointsQueue.size()).append(", ") //
					.append((pointsQueueSize == POINTS_QUEUE_SIZE) ? "!!!POINTS BACKPRESSURE!!!" : "") //
					.toString());
		}, 10, 10, TimeUnit.SECONDS);

		this.mergePointsExecutor.execute(() -> {
			// TODO stop on deactivate()
			/*
			 * Load Schema cache
			 */
			while (this.schema == null) {
				try {
					this.schema = Schema.initialize(this.dataSource);

				} catch (SQLException e) {
					this.logError(this.log, "Unable to cache Schema: " + e.getMessage());

					try {
						Thread.sleep(10000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}

			/**
			 * This task merges single Points to Lists of Points, which are then sent to
			 * TimescaleDB. This approach improves speed as not every single Point gets sent
			 * via HTTP individually.
			 */
			while (true) {
				try {
					// Poll and merge Points. Wait max 10 seconds in total.
					var points = pollAndMergePoints(this.pointsQueue);

					if (points.isEmpty()) {
						continue;
					}

					// Write points async.
					this.executor.execute(() -> {
						var psts = new EnumMap<Type, PreparedStatement>(Type.class);
						try (var con = this.dataSource.getConnection()) {
							// Prepare a PreparedStatement for every type
							for (var type : Type.values()) {
								psts.put(type, type.prepareStatement(con));
							}
							// Add data from points to PreparedStatements
							for (var point : points) {

								try {
									var channel = this.schema.getChannel(point, con);
									if (channel == null) {
										continue;
									}
									var pst = psts.get(channel.type);
									channel.type.fillStatement(pst, point, channel);
									pst.addBatch();
								} catch (Exception e) {
									this.logWarn(this.log, "Unable to add Point [" + point.edgeId + "/"
											+ point.channelAddress + ":" + point.value + "]: " + e.getMessage());
								}

							}
							// Execute all Batches
							for (var entry : psts.entrySet()) {
								try {
									entry.getValue().executeBatch();
								} catch (SQLException e) {
									this.logWarn(this.log,
											"Unable to write Batch [Type:" + entry.getKey() + "]: " + e.getMessage());
								}
							}

						} catch (SQLException e) {
							// 'Expected errors', e.g. PostgreSQL server stopped
							// -> short error log
							this.logError(this.log,
									"Unable to write Points. " + e.getClass().getSimpleName() + ": " + e.getMessage());

						} catch (Exception e) {
							// 'Unexpected errors' -> long stacktrace
							this.logError(this.log,
									"Unable to write Points. " + e.getClass().getSimpleName() + ": " + e.getMessage());
							e.printStackTrace();

						} finally {
							// Close PreparedStatements (Connection is autoclosed)
							for (var pst : psts.values()) {
								try {
									pst.close();
								} catch (SQLException e) {
									this.logWarn(this.log, "Unable to close PreparedStatement: " + e.getMessage());
								}
							}
						}
					});

				} catch (InterruptedException e) {
					this.log.info("MergePointsExecutor was interrupted");
					break;

				} catch (Throwable e) {
					this.log.error("Unhandled Error in 'MergePointsExecutor': " + e.getClass().getName() + ". "
							+ e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}

	@Deactivate
	private void deactivate() {
		this.logInfo(this.log, "Deactivate");
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 0);
		ThreadPoolUtils.shutdownAndAwaitTermination(this.mergePointsExecutor, 0);
		ThreadPoolUtils.shutdownAndAwaitTermination(this.debugLogExecutor, 0);
		if (this.dataSource != null) {
			this.dataSource.close();
		}
	}

	@Override
	public void write(String edgeId, TreeBasedTable<Long, ChannelAddress, JsonElement> data) throws OpenemsException {
		if (this.isReadOnly) {
			this.log.info("Read-Only-Mode is activated. Not writing points: "
					+ StringUtils.toShortString(data.toString(), 100));
			return;
		}

		this.pointsQueue.addAll(data.cellSet().stream() //
				.map(cell -> new Point(cell.getRowKey(), edgeId, cell.getColumnKey(), cell.getValue())) //
				.collect(Collectors.toList()));
	}

	/**
	 * Creates a {@link HikariDataSource} connection pool.
	 *
	 * @param host     the database hostname
	 * @param port     the database port
	 * @param database the database name
	 * @param user     the database user
	 * @param password the database password
	 * @return the HikariDataSource
	 * @throws SQLException on error
	 */
	private static HikariDataSource getDataSource(String host, int port, String database, String user, String password)
			throws SQLException {
		if (!Driver.isRegistered()) {
			Driver.register();
		}
		var pgds = new PGSimpleDataSource();
		pgds.setServerNames(new String[] { host });
		pgds.setPortNumbers(new int[] { port });
		pgds.setDatabaseName(database);
		pgds.setUser(user);
		pgds.setPassword(password);
		var result = new HikariDataSource();
		result.setDataSource(pgds);
		return result;
	}

	/**
	 * Poll and merge Points. Wait max 10 seconds in total.
	 * 
	 * @param pointsQueue the Queue of Points
	 * @return a list of Points
	 * @throws InterruptedException on error
	 */
	private static List<Point> pollAndMergePoints(BlockingQueue<Point> pointsQueue) throws InterruptedException {
		final Instant maxWait = Instant.now().plusSeconds(MAX_AGGREGATE_WAIT);
		List<Point> points = new ArrayList<>(MAX_POINTS_PER_WRITE);
		for (int i = 0; i < MAX_POINTS_PER_WRITE; i++) {
			var point = pointsQueue.poll(MAX_AGGREGATE_WAIT, TimeUnit.SECONDS);
			if (point == null) {
				break;
			}
			points.add(point);
			if (Instant.now().isAfter(maxWait)) {
				break;
			}
		}
		return points;
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {

		// handle empty call
		if (channels.isEmpty()) {
			return new TreeMap<>();
		}

		var result = Utils.prepareDataMap(fromDate, toDate, channels, resolution);
		var types = Utils.querySchemaCache(this.schema, edgeId, channels);

		// Open ONE database connection
		try (var con = this.dataSource.getConnection()) {

			// Execute specific query for each Type
			for (var typeEntry : types.entrySet()) {
				var type = typeEntry.getKey();
				var ids = typeEntry.getValue();

				// Build custom SQL for PreparedStatement
				var sql = "SELECT" //
						+ "    time_bucket(" //
						+ "        ?::interval," // [1] Resolution
						+ "        data.time)," //
						+ "    data.channel_id," //
						+ "    " + type.defaultAggregateFunction + "(data." + type.defaultAggregateFunction + ") " //
						+ "FROM " + type.tableAggregate5m + " data " //
						+ "WHERE" //
						+ "    data.channel_id IN (" //
						+ ids.keySet().stream() //
								.map(c -> "?") // [2++] Channel-ID
								.collect(Collectors.joining(",")) //
						+ "    ) AND" //
						+ "    data.time >= ? AND" // [n-1] FromDate
						+ "    data.time < ? " // [n] ToDate
						+ "GROUP BY 1,2";

				// Query the database
				try (var pst = con.prepareStatement(sql)) {
					// Fill PreparedStatement.

					// Reference for Java 8 Date and Time classes with PostgreSQL:
					// https://jdbc.postgresql.org/documentation/head/java8-date-time.html
					var i = 1;
					pst.setString(i++, Utils.toSqlInterval(resolution));
					for (var id : ids.keySet()) {
						pst.setInt(i++, id);
					}
					pst.setObject(i++, fromDate.toOffsetDateTime());
					pst.setObject(i++, toDate.toOffsetDateTime());

					var rs = pst.executeQuery();
					while (rs.next()) {
						var time = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(fromDate.getZone());
						var channelAddress = ids.get(rs.getInt(2));
						var value = type.parseValueFromResultSet(rs, 3);
						var resultTime = result.computeIfAbsent(time, t -> new TreeMap<>());
						resultTime.put(channelAddress, value);
					}

				} catch (SQLException e) {
					this.logError(this.log,
							"Unable to query historic data for type [" + type.name() + "]: " + e.getMessage());
					// TODO collect exceptions; throw error if everything fails
				}
			}
		} catch (SQLException e) {
			this.logError(this.log, "Unable to query historic data: " + e.getMessage());
			throw new OpenemsException("Error while querying historic data");
		}
		return result;
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {

		// handle empty call
		if (channels.isEmpty()) {
			return new TreeMap<>();
		}

		var result = Utils.prepareEnergyMap(fromDate, toDate, channels);
		var types = Utils.querySchemaCache(this.schema, edgeId, channels);

		// Open ONE database connection
		try (var con = this.dataSource.getConnection()) {

			// Execute specific query for each Type
			for (var typeEntry : types.entrySet()) {
				var type = typeEntry.getKey();
				var ids = typeEntry.getValue();

				// Build custom SQL for PreparedStatement
				// TODO evaluate if there is a better way then querying every channel separately
				var sql = "SELECT" //
						+ "    LAST(\"max\", time) - FIRST(\"min\", time) " //
						+ "FROM " + type.tableAggregate5m + " data " //
						+ "WHERE" //
						+ "    data.channel_id = ? AND" // [1] Channel-ID
						+ "    data.time >= ? AND" // [2] FromDate
						+ "    data.time < ?"; // [3] ToDate

				// Query the database
				try (var pst = con.prepareStatement(sql)) {
					// Fill PreparedStatement.

					for (var id : ids.entrySet()) {
						// Reference for Java 8 Date and Time classes with PostgreSQL:
						// https://jdbc.postgresql.org/documentation/head/java8-date-time.html
						var i = 1;
						pst.setInt(i++, id.getKey());
						pst.setObject(i++, fromDate.toOffsetDateTime());
						pst.setObject(i++, toDate.toOffsetDateTime());

						var rs = pst.executeQuery();
						while (rs.next()) {
							var channelAddress = id.getValue();
							var value = type.parseValueFromResultSet(rs, 1);
							result.put(channelAddress, value);
						}
					}

				} catch (SQLException e) {
					this.logError(this.log,
							"Unable to query historic energy for type [" + type.name() + "]: " + e.getMessage());
					// TODO collect exceptions; throw error if everything fails
				}
			}
		} catch (SQLException e) {
			this.logError(this.log, "Unable to query historic energy: " + e.getMessage());
			throw new OpenemsException("Error while querying historic energy");
		}
		return result;
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {

		// handle empty call
		if (channels.isEmpty()) {
			return new TreeMap<>();
		}

		var result = Utils.prepareDataMap(fromDate, toDate, channels, resolution);
		var types = Utils.querySchemaCache(this.schema, edgeId, channels);

		// Open ONE database connection
		try (var con = this.dataSource.getConnection()) {

			// Execute specific query for each Type
			for (var typeEntry : types.entrySet()) {
				var type = typeEntry.getKey();
				var ids = typeEntry.getValue();

				// Build custom SQL for PreparedStatement
				var sql = "SELECT" //
						+ "    timescaledb_experimental.time_bucket_ng(" //
						+ "        ?::interval," // [1] Resolution
						+ "        data.time," //
						+ "        timezone => ?)," // [2] timezone
						+ "    data.channel_id," //
						+ "    LAST(\"max\", data.time)" //
						+ "FROM " + type.tableAggregate5m + " data " //
						+ "WHERE" //
						+ "    data.channel_id IN (" //
						+ ids.keySet().stream() //
								.map(c -> "?") // [3++] Channel-ID
								.collect(Collectors.joining(",")) //
						+ "    ) AND" //
						+ "    data.time >= ? AND" // [n-1] FromDate
						+ "    data.time < ? " // [n] ToDate
						+ "GROUP BY 1,2";

				// Query the database
				var data = new TreeMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>>();
				try (var pst = con.prepareStatement(sql)) {
					// Fill PreparedStatement.

					// Reference for Java 8 Date and Time classes with PostgreSQL:
					// https://jdbc.postgresql.org/documentation/head/java8-date-time.html
					var i = 1;
					pst.setString(i++, Utils.toSqlInterval(resolution));
					pst.setString(i++, fromDate.getZone().getId());
					for (var id : ids.keySet()) {
						pst.setInt(i++, id);
					}
					pst.setObject(i++, fromDate.minus(resolution.getValue(), resolution.getUnit()).toOffsetDateTime());
					pst.setObject(i++, toDate.toOffsetDateTime());

					var rs = pst.executeQuery();
					while (rs.next()) {
						var time = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(fromDate.getZone());
						var channelAddress = ids.get(rs.getInt(2));
						var value = type.parseValueFromResultSet(rs, 3);
						var dataTime = data.computeIfAbsent(time, t -> new TreeMap<>());
						dataTime.put(channelAddress, value);
					}

				} catch (SQLException e) {
					this.logError(this.log,
							"Unable to query historic data for type [" + type.name() + "]: " + e.getMessage());
					// TODO collect exceptions; throw error if everything fails
				}

				// Calculate delta
				SortedMap<ChannelAddress, JsonElement> lastEntry = null;
				for (var entry : data.entrySet()) {
					if (lastEntry != null) { // ignore first entry with time t-1
						var time = entry.getKey();
						for (var id : ids.entrySet()) {
							var channelAddress = id.getValue();
							var lastValue = lastEntry.get(id.getValue());
							var thisValue = entry.getValue().get(channelAddress);
							var resultTime = result.computeIfAbsent(time, t -> new TreeMap<>());
							resultTime.put(channelAddress, type.subtract(thisValue, lastValue));
						}
					}
					lastEntry = entry.getValue();
				}
			}
		} catch (SQLException e) {
			this.logError(this.log, "Unable to query historic data: " + e.getMessage());
			throw new OpenemsException("Error while querying historic data");
		}
		return result;
	}

	@Override
	public Map<ChannelAddress, JsonElement> getChannelValues(String edgeId, Set<ChannelAddress> channelAddresses) {
		// TODO
		return Collections.emptyMap();
	}

}
