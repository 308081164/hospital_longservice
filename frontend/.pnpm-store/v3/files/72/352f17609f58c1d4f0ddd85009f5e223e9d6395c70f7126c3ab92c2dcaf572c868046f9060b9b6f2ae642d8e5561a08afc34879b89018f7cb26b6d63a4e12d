/**
 * @typedef {{
*   lineWidth?: number,
*   dpi?: number,
*   alpha?: number,
*   gradient?: Array<number>,
*   gradientColors?: Array<{ start: number, color:number }>,
*   strokeStyle?: string,
*   fillColor?: string,
*   height?: number,
*   data?: Array<number>,
*   maxValue?: number,
*   minValue?: number,
*   maxLength?: number,
*   mode?: string,
*   [propName: string]: any
* }} IHeatMapConfig
*/
export default class HeatMap extends Plugin {
    /**
     * @type IHeatMapConfig
     */
    static get defaultConfig(): IHeatMapConfig;
    constructor(args: any);
    canvasHeight: number;
    canvasWidth: number;
    width: number;
    height: number;
    dataFloatLen: number;
    xData: any[];
    yData: any[];
    yMax: number;
    _duration: number;
    curData: any[];
    setConfig(config: any): void;
    reset(): void;
    createRoot(): void;
    root: HTMLElement;
    canvas: HTMLElement;
    resize(): void;
    formatData(data: any, duration: any, minValue: any): any[];
    _getX(index: any, stepX: any, item: any, width: any): number;
    _getY(item: any, stepY: any, maxY: any): any;
    setData(data: any): void;
    _getFillStyle(ctx: any): any;
    drawLinePath(): void;
    clearCanvas(): void;
    fixFloat(_num: any, _length: any): number;
    render(): string;
}
export type IHeatMapConfig = {
    [propName: string]: any;
    lineWidth?: number;
    dpi?: number;
    alpha?: number;
    gradient?: Array<number>;
    gradientColors?: Array<{
        start: number;
        color: number;
    }>;
    strokeStyle?: string;
    fillColor?: string;
    height?: number;
    data?: Array<number>;
    maxValue?: number;
    minValue?: number;
    maxLength?: number;
    mode?: string;
};
import Plugin from "../../plugin";
