export default Volume;
export type IVolumeConfig = {
    [propName: string]: any;
    position?: string;
    index?: number;
    disable?: boolean;
    showValueLabel?: boolean;
    default?: number;
};
/**
 * @typedef {{
 *   position?: string, // [可选]插件挂载的dom
 *   index?: number // [可选]插件在播放器中挂载的位置
 *   disable?: boolean, // [可选]是否禁用插件交互行为
 *   showValueLabel?: boolean, // [可选]是否显示当前滑动的音量数值
 *   default?: number // [可选]默认
 *   [propName: string]: any
 * }} IVolumeConfig
 */
declare class Volume extends Plugin {
    /**
     * @type IVolumeConfig
     */
    static get defaultConfig(): IVolumeConfig;
    registerIcons(): {
        volumeSmall: {
            icon: any;
            class: string;
        };
        volumeLarge: {
            icon: any;
            class: string;
        };
        volumeMuted: {
            icon: any;
            class: string;
        };
    };
    _timerId: any;
    _d: {
        isStart: boolean;
        isMoving: boolean;
        isActive: boolean;
    };
    changeMutedHandler: any;
    _onMouseenterHandler: any;
    _onMouseleaveHandler: any;
    onBarMousedown: (e: any) => boolean;
    pos: {
        x: number;
        y: number;
        clientX: number;
        clientY: number;
        offsetX: number;
        offsetY: number;
        pageX: number;
        pageY: number;
    };
    onBarMouseMove: (e: any) => void;
    onBarMouseUp: (e: any) => void;
    updateVolumePos(height: any, event: any): void;
    /**
     * 修改音量数值标签
     *
     * @memberof Volume
     */
    updateVolumeValue(): void;
    /**
     * @desc 聚焦
     */
    focus(): void;
    /**
     * 失去焦点
     * @param { number } delay 延迟隐藏时长，ms
     * @param { boolean } isForce 是否立即隐藏控制栏
     * @param { Event} [e] 事件
     * @returns
     */
    unFocus(delay?: number, isForce?: boolean, e?: Event): void;
    onMouseenter: (e: any) => void;
    onMouseleave: (e: any) => void;
    changeMuted(e: any): void;
    onVolumeChange: (e: any) => void;
    animate(muted: any, volume: any): void;
    initIcons(): void;
    render(): string;
}
import Plugin from "../../plugin";
