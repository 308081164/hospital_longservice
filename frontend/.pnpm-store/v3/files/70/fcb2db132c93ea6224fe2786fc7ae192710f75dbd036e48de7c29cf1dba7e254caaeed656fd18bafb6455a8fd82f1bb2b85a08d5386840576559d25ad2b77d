export default Progress;
export type IProgressConfig = {
    [propName: string]: any;
    position?: string;
    disable?: boolean;
    isDraggingSeek?: boolean;
    closeMoveSeek?: boolean;
    isPauseMoving?: boolean;
    isCloseClickSeek?: boolean;
    fragments?: Array<{
        percent: number;
    }>;
    fragFocusClass?: string;
    fragAutoFocus?: boolean;
    miniMoveStep?: number;
    miniStartStep?: number;
    onMoveStart?: () => any;
    onMoveEnd?: () => any;
    endedDiff?: number;
};
declare class Progress extends Plugin {
    /**
     * @type IProgressConfig
     */
    static get defaultConfig(): IProgressConfig;
    static get FRAGMENT_FOCUS_CLASS(): {
        POINT: string;
        HIGHLIGHT: string;
    };
    constructor(args: any);
    /**
     * @readonly
     */
    readonly useable: boolean;
    /**
     * @readonly
     */
    readonly isProgressMoving: boolean;
    /**
     * @private
     */
    private __dragCallBacks;
    /**
     * @private
     */
    private _state;
    _disableBlur: boolean;
    get offsetDuration(): number;
    get duration(): number;
    get timeOffset(): number;
    get currentTime(): any;
    changeState(useable?: boolean): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    /**
     * @description 创建内部进度条，并挂载到xg-outer上,
     *              并把一些对外API绑定在progress上供外部调用
     *
     */
    _initInner(fragments?: any[], config?: {}): void;
    innerList: InnerList;
    _updateInnerFocus(data: any): void;
    pos: {
        x: number;
        y: number;
        moving: boolean;
        isDown: boolean;
        isEnter: boolean;
        isLocked: boolean;
    };
    outer: HTMLElement;
    isMobile: boolean;
    progressBtn: HTMLElement;
    /**
     * This method can be overridden.
     * Eg. xgplayer-ads/ui/adProgress.js
     */
    listenEvents(): void;
    /**
     * @description 配置更新响应，插件内置调用api, 播放器整体配置更新的时候调用
     * @param {IProgressConfig} config
     */
    setConfig(config: IProgressConfig): void;
    initCustomStyle(): void;
    /**
     * 触发某一类回调监听
     * @param { string } type 类型 drag/dragend
     * @param { any} data 具体数据
     */
    triggerCallbacks(type: string, data: any, event: any): void;
    /**
     * 供外部插件添加回调
     * @param {string} type 类型 drag/dragend
     * @param {function} handle 回调函数句柄
     */
    addCallBack(type: string, handle: Function): void;
    /**
     * 供外部插件移除回调
     * @param {string} type 类型 drag/dragend
     * @param {Function} event 回调函数句柄
     */
    removeCallBack(type: string, event: Function): void;
    /**
     * @description 解除进度条的所动状态
     * @returns
     */
    unlock(): void;
    bindDomEvents(): void;
    _mouseDownHandlerHook: any;
    _mouseUpHandlerHook: any;
    _mouseMoveHandlerHook: any;
    focus(): void;
    blur(): void;
    disableBlur(): void;
    enableBlur(): void;
    onMoveOnly: (e: any, data: any) => void;
    /**
     * 避免mouseup的时候触发父辈节点的click事件，和单击视频区域切换暂停/播放互斥
     * @param {*} e
     * @returns
     */
    onBodyClick: (e: any) => void;
    _mouseDownHandler: (event: any, data: any) => void;
    _mouseUpHandler: (e: any, data: any) => void;
    _mouseMoveHandler: (e: any, data: any) => void;
    onMouseDown: (e: any) => boolean;
    onMouseUp: (e: any) => void;
    onMouseMove: (e: any) => void;
    onMouseOut: (e: any) => void;
    onMouseOver: (e: any) => void;
    onMouseEnter: (e: any) => void;
    onMouseLeave: (e: any) => void;
    onVideoResize: () => void;
    /**
     * @description 根据currenTime和占用百分比更新进度条
     * @param {number} currentTime 需要更新到的时间
     * @param {number} seekTime 实际seek的时间
     * @param {number} percent 更新时间占比
     * @param {number} type 触发类型 0-down 1-move 2-up
     */
    updateWidth(currentTime: number, seekTime: number, percent: number, type: number): void;
    computeTime(e: any, x: any): {
        percent: number;
        currentTime: number;
        seekTime: any;
        offset: number;
        width: number;
        left: number;
        e: any;
    };
    /**
     * @description 更新时间插件，在拖拽状态下要接管时间插件的更新状态
     *             本位置会和time插件交互
     * @param {number} time 根据拖拽距离计算出的时间
     */
    updateTime(time: number): void;
    /**
     * @description 复位正在拖拽状态 ，拖拽的时候要避免timeupdate更新
     */
    resetSeekState(): void;
    /**
     * @description 拖拽过程中更新UI
     * @param {number} percent 小于0的小数
     *
     */
    updatePercent(percent: number, notSeek: any): void;
    /**
     * @description 播放进度更新
     * @param { boolean } isEnded 是否是播放结束的时候调用
     * @returns
     */
    onTimeupdate(isEnded: boolean): void;
    /**
     * @description 缓存进度更新
     * @param { boolean } isEnded 是否是结束时触发
     * @returns
     */
    onCacheUpdate(isEnded: boolean): void;
    onReset(): void;
    thumbnailPlugin: any;
    render(): string;
}
import Plugin from "../../plugin";
import InnerList from "./innerList";
