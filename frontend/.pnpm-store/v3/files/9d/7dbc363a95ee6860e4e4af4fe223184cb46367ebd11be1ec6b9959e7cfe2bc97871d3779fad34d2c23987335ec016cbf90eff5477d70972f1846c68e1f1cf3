export default MobilePlugin;
export type IMobileConfig = {
    [propName: string]: any;
    index?: number;
    disableGesture?: boolean;
    gestureX?: boolean;
    gestureY?: boolean;
    gradient?: 'normal' | 'none' | 'top' | 'bottom';
    isTouchingSeek?: boolean;
    miniMoveStep?: number;
    miniYPer?: number;
    scopeL?: number;
    scopeR?: number;
    pressRate?: number;
    darkness?: boolean;
    maxDarkness?: number;
    disableActive?: boolean;
    disableTimeProgress?: boolean;
    hideControlsActive?: boolean;
    hideControlsEnd?: boolean;
    moveDuration?: number;
    closedbClick?: boolean;
    disablePress?: boolean;
    disableSeekIcon?: boolean;
    focusVideoClick?: boolean;
};
declare class MobilePlugin extends Plugin {
    /**
     * @type IMobileConfig & { [propName: string]: any}
     */
    static get defaultConfig(): IMobileConfig & {
        [propName: string]: any;
    };
    constructor(options: any);
    /**
     * @readonly
     */
    readonly pos: {
        isStart: boolean;
        x: number;
        y: number;
        time: number;
        volume: number;
        rate: number;
        light: number;
        width: number;
        height: number;
        scopeL: number;
        scopeR: number;
        scopeM1: number;
        scopeM2: number;
        scope: number;
    };
    /**
     * @private
     */
    private timer;
    get duration(): number;
    get timeOffset(): number;
    registerIcons(): {
        seekTipIcon: {
            icon: any;
            class: string;
        };
    };
    xgMask: HTMLElement;
    touch: Touche;
    registerThumbnail(): void;
    thumbnail: any;
    initCustomStyle(): void;
    resetPos(time?: number): void;
    changeAction(action: any): void;
    getTouche(e: any): {
        pageX: any;
        pageY: any;
    };
    /**
     * 校验具体的操作范围
     * @param { number } x 方向位移
     * @param { number } y 方向位移
     * @param { number } diffx 方向位移差
     * @param { number } diffy 方向位移差
     * @param { any } pos 当前操作区域位置信息 包含{width, height}
     * @return { number } scope 区域 0=>中间x方向滑动  1=>左侧上下滑动 2=>右侧上下滑动
     */
    checkScope(x: number, y: number, diffx: number, diffy: number, pos: any): number;
    /**
     * 根据位移和操作类型进行对应处理
     * @param {number} diffx x方向位移差
     * @param {number} diffy y方向位移差
     * @param {number} scope scope 区域 0=>中间x方向滑动  1=>左侧上下滑动 2=>右侧上下滑动
     * @param {number} width 总体宽度
     * @param {number} height 总高度
     */
    executeMove(diffx: number, diffy: number, scope: number, width: number, height: number): void;
    /**
     * 结束手势操作
     * @param {number} scope 当前手势类型 区域 0=>中间x方向滑动  1=>左侧上下滑动 2=>右侧上下滑动
     * @param {number} lastScope 上一次手势类型
     */
    endLastMove(lastScope: number): void;
    onTouchStart: (e: any) => void;
    onTouchMove: (e: any) => void;
    onTouchEnd: (e: any) => void;
    checkIsRootTarget(e: any): any;
    onRootTouchMove: (e: any) => void;
    onRootTouchEnd: (e: any) => void;
    sendUseAction(event: any): void;
    clickHandler(e: any): void;
    dbClickHandler(e: any): void;
    onClick(e: any): void;
    onDbClick(e: any): void;
    onPress(e: any): void;
    onPressEnd(e: any): void;
    updateTime(percent: any): void;
    updateVolume(percent: any): void;
    updateBrightness(percent: any): void;
    activeSeekNote(time: any, isForward?: boolean): void;
    updateThumbnails(time: any): void;
    switchPlayPause(): boolean;
    disableGesture(): void;
    enableGesture(): void;
    render(): string;
}
import Plugin from "../../plugin";
import Touche from "./touch";
