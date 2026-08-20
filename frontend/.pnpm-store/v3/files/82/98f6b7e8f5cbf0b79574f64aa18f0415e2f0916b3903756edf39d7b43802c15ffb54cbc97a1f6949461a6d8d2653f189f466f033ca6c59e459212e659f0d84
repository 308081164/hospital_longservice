/**
 * @typedef { {
 *   position?: string,
 *   index?: number,
 *   useCssFullscreen?: boolean,
 *   rotateFullscreen?: boolean,
 *   useScreenOrientation?: boolean,
 *   lockOrientationType?: OrientationType,
 *   switchCallback?: () => any,
 *   target?: null | HTMLElement,
 *   disable?: boolean,
 *   needBackIcon?: boolean,
 *   [propName: string]: any
 * } } IFullscreenConfig
 */
export default class Fullscreen extends IconPlugin {
    /**
     * @type IFullscreenConfig
     */
    static get defaultConfig(): IFullscreenConfig;
    handleFullscreen: any;
    topBackIcon: any;
    /**
     * @private
     */
    private _onOrientationChange;
    registerIcons(): {
        fullscreen: {
            icon: any;
            class: string;
        };
        exitFullscreen: {
            icon: any;
            class: string;
        };
    };
    initIcons(): void;
    /**
     * 切换全屏
     * @param { Event } [e]
     */
    toggleFullScreen(e?: Event): void;
    /**
     *
     * @param { boolean } isFullScreen
     */
    animate(isFullScreen: boolean): void;
    /**
     * @returns
     */
    render(): string;
    /**
     * 锁定屏幕方向，只有部分移动端浏览器支持
     * 兼容性参考：https://caniuse.com/mdn-api_screenorientation_lock
     * @param {OrientationType} orientation
     */
    lockScreen(orientation: OrientationType): void;
    /**
     * 解锁屏幕方向锁定，只有部分移动端浏览器支持
     */
    unlockScreen(): void;
}
export type IFullscreenConfig = {
    [propName: string]: any;
    position?: string;
    index?: number;
    useCssFullscreen?: boolean;
    rotateFullscreen?: boolean;
    useScreenOrientation?: boolean;
    lockOrientationType?: OrientationType;
    switchCallback?: () => any;
    target?: null | HTMLElement;
    disable?: boolean;
    needBackIcon?: boolean;
};
import IconPlugin from "../common/iconPlugin";
