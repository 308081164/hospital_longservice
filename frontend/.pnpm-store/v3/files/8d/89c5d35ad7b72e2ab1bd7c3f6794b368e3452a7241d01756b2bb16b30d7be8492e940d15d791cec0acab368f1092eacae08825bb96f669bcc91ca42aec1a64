/**
  * @typedef { {
  *  position: string,
  *  index: number,
  *  disable: boolean,
  *  target: null | HTMLElement,
  *  [propName: string]: any
  * } } ICssConfig
  */
export default class CssFullScreen extends IconPlugin {
    /**
     * @type ICssConfig
     */
    static get defaultConfig(): ICssConfig;
    beforeCreate(args: any): void;
    btnClick(e: any): void;
    handleCssFullscreen: any;
    initIcons(): void;
    animate(isFullScreen: any): void;
    switchTips(isFullScreen: any): void;
    registerIcons(): {
        cssFullscreen: {
            icon: any;
            class: string;
        };
        exitCssFullscreen: {
            icon: any;
            class: string;
        };
    };
    render(): string;
}
export type ICssConfig = {
    [propName: string]: any;
    position: string;
    index: number;
    disable: boolean;
    target: null | HTMLElement;
};
import IconPlugin from "../common/iconPlugin";
