export default Poster;
export type IPosterConfig = {
    isEndedShow?: boolean;
    hideCanplay?: boolean;
    poster?: string;
};
/**
 * @typedef {{
 *   isEndedShow?: boolean, // 是否在播放结束之后显示
 *   hideCanplay?: boolean, // 设置为true时，播放后才隐藏，在视频地址更新后会重新显示poster。默认为false，即在play事件触发后隐藏poster
 *   poster?: string // 封面图地址
 * }} IPosterConfig
 */
declare class Poster extends Plugin {
    /**
     * @type IPosterConfig
     */
    static get defaultConfig(): IPosterConfig;
    set isEndedShow(arg: any);
    get isEndedShow(): any;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    beforeCreate(args: any): void;
    setConfig(config: any): void;
    onTimeUpdate(): void;
    update(poster: any): void;
    getBgSize(mode: any): string;
    render(): string;
}
import Plugin from "../../plugin";
