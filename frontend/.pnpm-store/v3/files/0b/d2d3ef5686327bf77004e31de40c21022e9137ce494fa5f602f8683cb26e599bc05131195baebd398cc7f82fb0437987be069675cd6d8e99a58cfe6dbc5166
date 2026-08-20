/**
 * @typedef {{
 *   isShow?: boolean,
 *   urls?: Array<string>, // 有多张大图就多个url
 *   pic_num?: number, // 每张图含有几个雪碧图
 *   col?: number, // 截图列数
 *   row?: number, // 截图行数
 *   height?: number, // 缩略图高度
 *   width?: number, // 缩略图宽度
 *   scale?: number, // 缩放
 *   className?: string, // 额外添加在dom上的class
 *   hidePortrait?: boolean, // 是否在竖屏的时候隐藏
 *   [propName: string]: any
 * }} IThumbnailConfig
 */
export default class Thumbnail extends BasePlugin {
    /**
     * @type IThumbnailConfig
     */
    static get defaultConfig(): IThumbnailConfig;
    constructor(args: any);
    ratio: number;
    interval: any;
    _preloadMark: {};
    setConfig(config: any): void;
    get usable(): boolean;
    initThumbnail(): void;
    getUrlByIndex(index: any): any;
    preload(index: any): void;
    getPosition(now: any, containerWidth?: number, containerHeight?: number): {
        urlIndex: number;
        rowIndex: number;
        colIndex: number;
        url: any;
        height: number;
        width: number;
        style: {
            backgroundImage: string;
            backgroundSize: string;
            backgroundPosition: string;
            width: string;
            height: string;
        };
    };
    update(dom: any, now: any, containerWidth?: number, containerHeight?: number, customStyle?: string): void;
    changeConfig(newConfig?: {}): void;
    createThumbnail(root: any, className: any): HTMLElement;
}
export type IThumbnailConfig = {
    [propName: string]: any;
    isShow?: boolean;
    urls?: Array<string>;
    pic_num?: number;
    col?: number;
    row?: number;
    height?: number;
    width?: number;
    scale?: number;
    className?: string;
    hidePortrait?: boolean;
};
import BasePlugin from "../../plugin";
