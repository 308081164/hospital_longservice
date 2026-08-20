/** */
export default class ProgressPreview extends Plugin {
    /**
     * @type IProgressPreviewConfig
     */
    static get defaultConfig(): IProgressPreviewConfig;
    constructor(args: any);
    _ispots: any[];
    videoPreview: HTMLElement;
    videothumbnail: any;
    thumbnail: any;
    timeStr: string;
    _state: {
        now: number;
        f: boolean;
    };
    beforeCreate(args: any): void;
    _curDot: any;
    handlerSpotClick: any;
    transformTimeHook: any;
    extTextRoot: HTMLElement;
    setConfig(config: any): void;
    previewLine: HTMLElement;
    timePoint: HTMLElement;
    timeText: HTMLElement;
    tipText: HTMLElement;
    _hasThumnail: boolean;
    bindEvents(): void;
    handlerPreviewClick: (e: any) => void;
    onMousemove: (e: any) => void;
    onMousedown: (e: any) => void;
    onMouseup: (e: any) => void;
    onDotMouseLeave: (e: any) => void;
    onProgressMouseOver: (data: any, e: any) => void;
    onProgressMove(data: any, e: any): void;
    onProgressDragStart(data: any): void;
    isDrag: boolean;
    onProgressDragEnd(data: any): void;
    onProgressClick(data: any, e: any): void;
    updateLinePos(offset: any, cwidth: any): void;
    updateTimeText(timeStr: any): void;
    updatePosition(offset: any, cwidth: any, time: any, e: any): void;
    /**
     * @description 设置自定义时间显示字符串
     * @param {string} str
     */
    setTimeContent(str: string): void;
    updateThumbnails(time: any): void;
    registerThumbnail(thumbnailConfig?: {}): void;
    calcuPosition(time: any, duration: any): {
        left: number;
        width: number;
        isMini: boolean;
    };
    showDot(id: any): void;
    focusDot(target: any, id: any): void;
    _activeDotId: any;
    blurDot(target: any): void;
    showTips(text: any, isDefault: any, timeStr?: string): void;
    hideTips(): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    render(): string;
}
export type IProgressPreviewConfig = {
    [propName: string]: any;
    miniWidth?: number;
    ispots?: {
        time?: number;
        text?: string;
        id?: number | string;
        duration: number | null;
        color?: string;
        style?: {
            [propName: string]: any;
        };
        width?: number;
        height?: number;
    }[];
    defaultText?: '';
    isFocusDots?: true;
    isShowThumbnail?: true;
    isShowCoverPreview?: false;
    mode?: 'short' | 'production';
};
import Plugin from "../../plugin";
