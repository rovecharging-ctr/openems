import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AbstractModalLine } from "../abstract-modal-line";

@Component({
    selector: 'oe-modal-line',
    templateUrl: './modal-line.html',
})
export class ModalLineComponent extends AbstractModalLine {

    // Width of Left Column, Right Column is (100% - leftColumn)
    @Input()
    leftColumnWidth: number;

    /** ControlName for Form Field */
    @Input() controlName: string;

    /** ControlName for Toggle Button */
    @Input() control:
        { type: 'TOGGLE' } |
        { type: 'INPUT' } |
        /* the available select options*/
        { type: 'SELECT', options: { value: string, name: string }[] } |
        /* the properties for range slider*/
        { type: 'RANGE', properties: { min: number, max: number, unit: 'H' } };

    /** Fixed indentation of the modal-line */
    @Input() textIndent: TextIndentation = TextIndentation.NONE;
}

export enum TextIndentation {
    NONE = '0%',
    SIMPLE = '5%',
    DOUBLE = '10%'
}
