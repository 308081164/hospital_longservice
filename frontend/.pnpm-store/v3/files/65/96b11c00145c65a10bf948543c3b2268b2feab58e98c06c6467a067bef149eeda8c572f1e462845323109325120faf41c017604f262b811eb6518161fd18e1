export default class TextTrack extends OptionsIcon {
    /**
     * @type ITextTrackConfig
     */
    static get defaultConfig(): ITextTrackConfig;
    beforeCreate(args: any): void;
    curIndex: any;
    lastIndex: any;
    _nativeTracks: any;
    handlerClickSwitch: any;
    /**
     * @description 初始化原生字幕
     *
     */
    _initNativeSubtitle(): void;
    subTitles: any;
    /**
     * 初始化外挂字幕
     * @param {number} defaultIndex
     */
    _initExtSubTitle(defaultIndex: number): void;
    _renderList(list: any, isDefaultOpen: any, defaultIndex: any): void;
    registerIcons(): {
        textTrackOpen: {
            icon: string;
            class: string;
        };
        textTrackClose: {
            icon: string;
            class: string;
        };
    };
    _onOff: () => void;
    _onChange: (data: any) => void;
    _onListReset: (data: any) => void;
    getSubTitleIndex(list: any, subtitle?: {
        id: string;
        language: string;
    }): number;
    /**
     *
     * @param { Array<SubTitleItem> } list
     * @param { boolean } needRemove 是否移除原来的字幕
     */
    updateSubtitles(list?: Array<SubTitleItem>, needRemove?: boolean): void;
    updateList(data?: {}): void;
    /**
     *
     * @param {{
     *   language?: string | number,
     *   id?: number | string,
     * }} subtitle
     * @returns
     */
    switchSubTitle(subtitle?: {
        language?: string | number;
        id?: number | string;
    }): void;
    /**
     * 关闭字幕
     */
    switchOffSubtitle(e: any): void;
    switchOnSubtitle(): void;
    /**
     * 切换按钮状态
     * @param {boolean} isopen
     */
    switchIconState(isopen: boolean): void;
    clickSwitch: (e: any, data: any) => void;
    onItemClick(e: any, data: any, ...args: any[]): void;
    updateCurItem(cIndex: any, subtitle: any): void;
    renderItemList(): void;
    onPlayerFocus: (e: any) => void;
    onPlayerBlur: (e: any) => void;
    rePosition(): void;
}
export type ListItem = {
    start: number;
    end: number;
    list: {
        [propName: string]: any;
        start: number;
        end: number;
        text: string[];
        index?: number;
    }[];
};
export type SubTitleItem = {
    language?: string | number;
    id?: number | string;
    isDefault?: boolean;
    text?: any;
    url?: string;
    stringContent?: string;
    list?: {
        [propName: string]: any;
        start: number;
        end: number;
        list: Array<ListItem>;
    }[];
};
export type ITextTrackConfig = {
    [propName: string]: any;
    position?: string;
    index?: number;
    list?: Array<SubTitleItem>;
    isDefaultOpen?: boolean;
    style?: {
        follow: boolean | null;
        mode?: 'stroke' | 'bg';
        followBottom?: number;
        fitVideo?: boolean;
        offsetBottom?: number;
        baseSizeX?: number;
        baseSizeY?: number;
        minSize?: number;
        minMobileSize?: number;
        line?: 'double' | 'single' | 'three';
        fontColor?: string;
    };
    closeText?: {
        text: string;
        iconText: string;
    };
    className?: string;
    hidePortrait?: boolean;
    isShowIcon?: boolean;
    updateMode?: 'live' | 'vod';
    renderMode?: 'normal';
    debugger?: boolean;
};
import OptionsIcon from "../common/optionsIcon";
