export type IDanmuConfig = {
    comments?: Array<any>;
    area?: {
        start: number;
        end: number;
    };
    closeDefaultBtn?: boolean;
    panel?: boolean;
    panelConfig?: {
        [propName: string]: any;
    };
    switchConfig?: {
        [propName: string]: any;
    };
    defaultOpen?: boolean;
    isLive?: boolean;
    channelSize?: number;
    fontSize?: number;
    opacity?: number;
    mouseControl?: boolean;
    mouseControlPause?: boolean;
    ext: {
        [propName: string]: any;
    };
    style: {
        [propName: string]: any;
    };
};
/**
 * @typedef {{
 *   comments?: Array<any>,
 *   area?: {
 *      start: number,
 *      end: number
 *   },
 *   closeDefaultBtn?: boolean,
 *   panel?: boolean,
 *   panelConfig?: {[propName: string]: any},
 *   switchConfig?: {[propName: string]: any},
 *   defaultOpen?: boolean,
 *   isLive?: boolean,
 *   channelSize?: number,
 *   fontSize?: number,
 *   opacity?: number,
 *   mouseControl?: boolean,
 *   mouseControlPause?: boolean,
 *   ext: {[propName: string]: any},
 *   style: {[propName: string]: any}
 * }} IDanmuConfig
 */
declare class Danmu extends Plugin {
    /**
     * @type IDanmuConfig
     */
    static get defaultConfig(): IDanmuConfig;
    constructor(args: any);
    danmujs: any;
    danmuPanel: any;
    isOpen: boolean;
    seekCost: number;
    /**
     * @readonly
     * @type {number}
     */
    readonly intervalId: number;
    /**
     * @readonly
     */
    readonly isUseClose: boolean;
    initDanmu(): void;
    registerExtIcons(): void;
    danmuButton: any;
    changeSet(set: any): void;
    onSwitch(event: any, defaultOpen: any): void;
    start(): void;
    stop(): void;
    clear(): void;
    setCommentLike(id: any, data: any): void;
    setCommentDuration(id: any, duration: any): void;
    setAllDuration(mode: any, duration: any): void;
    setCommentID(oldID: any, newID: any): void;
    hideMode(mode: any): void;
    showMode(mode: any): void;
    setArea(area: any): void;
    setOpacity(opacity: any): void;
    setFontSize(size: any, channelSize: any): void;
    resize(): void;
    sendComment(comments: any): void;
    updateComments(comments: any, isClear: any): void;
    hideIcon(): void;
    showIcon(): void;
    render(): string;
}
import DanmuIcon from "./danmuIcon";
import DanmuPanel from "./danmuPanel";
import Plugin from "../../plugin";
export { Danmu as default, DanmuIcon, DanmuPanel };
