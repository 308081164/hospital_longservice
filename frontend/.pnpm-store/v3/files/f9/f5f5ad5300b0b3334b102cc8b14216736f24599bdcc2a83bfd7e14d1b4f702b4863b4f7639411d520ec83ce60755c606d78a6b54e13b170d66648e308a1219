export default DynamicBg;
export type IDynamicBgConfig = {
    [propName: string]: any;
    disable?: boolean;
    mode?: "realtime" | "firstframe" | "framerate";
    frameRate?: number;
    filter?: string;
    addMask?: boolean;
    maskBg?: string;
    multiple?: number;
};
declare class DynamicBg extends Plugin {
    /**
     * @type IDynamicBgConfig
     */
    static get defaultConfig(): IDynamicBgConfig;
    /**
     * @type {boolean}
     * @description Does the current environment support Canvas
     */
    static get isSupport(): boolean;
    static supportCanvasFilter(): boolean;
    /**
     * @private
     */
    private _pos;
    /**
     * @readonly
     */
    readonly isStart: boolean;
    _isLoaded: boolean;
    /**
     * @readonly
     */
    readonly videoPI: number;
    /**
     * @readonly
     */
    readonly preTime: number;
    /**
     * @readonly
     */
    readonly interval: number;
    /**
     * @readonly
     */
    readonly canvas: HTMLElement;
    /**
     * @readonly
     */
    readonly canvasCtx: any;
    _frameCount: any;
    _loopType: string;
    setConfig(config: any): void;
    onLoadedData: (e: any) => void;
    onVisibilitychange: (e: any) => void;
    /**
     * @private
     * 初始化 canvas对象并使用海报图先渲染首帧w11
     */
    private init;
    canvasFilter: boolean;
    root: HTMLElement;
    mask: HTMLElement;
    reRender(root: any): void;
    /**
   * Check whether the current video object supports screenshots
   * @param { Object } video
   * @returns { DomElement | null }
   */
    checkVideoIsSupport(video: any): DomElement | null;
    renderByPoster(): void;
    renderOnTimeupdate: (e: any) => void;
    _checkIfCanStart(): boolean;
    /**
     * just render once
     * @returns
     */
    renderOnce(): void;
    start: (time: any, interval: any) => void;
    frameId: any;
    stop: () => void;
    updateImg(url: any): void;
    update(video: any, sourcePI: any): void;
    render(): string;
}
import Plugin from "../../plugin";
