/**
 * @typedef { string | MediaStream | Array<{src: string, type?:string, [propName: string]: any}> } IUrl
 */
/**
 * @typedef {{
 *   url: IUrl,
 *   definition: any,
 *   bitrate?: number,
 *   bandwidth?: number,
 *   text?: string | { [propName: string]: any },
 *   iconText?: string | { [propName: string]: any },
 *   [propName: string]: any
 * }} IDefinition
 */
/**
 * @typedef { {
 *   id?: string,
 *   el?: HTMLElement,
 *   mediaEl?: HTMLMediaElement | (mediaConfig: { [propName: string]: any }) => HTMLMediaElement,
 *   url?: IUrl,
 *   domEventType?: 'default' | 'touch' | 'mouse',
 *   nullUrlStart?: boolean,
 *   width?: number | string,
 *   height?: number | string,
 *   fluid?: boolean,
 *   fitVideoSize?: 'fixWidth'|'fixHeight'|'fixed',
 *   videoFillMode?: 'auto'|'fillHeight'|'fillWidth'|'fill'|'cover'|'contain',
 *   volume?: number | { [propName: string]: any },
 *   autoplay?: boolean,
 *   autoplayMuted?: boolean,
 *   loop?: boolean,
 *   isLive?: boolean,
 *   zoom?: number,
 *   videoInit?: boolean,
 *   poster?: string | { [propName: string]: any },
 *   isMobileSimulateMode?: 'mobile' | 'pc',
 *   defaultPlaybackRate?: number,
 *   execBeforePluginsCall?: () => any,
 *   allowSeekAfterEnded?: boolean,
 *   enableContextmenu?: boolean,
 *   closeVideoClick?: boolean,
 *   closeVideoDblclick?: boolean,
 *   closePlayerBlur?: boolean,
 *   closeDelayBlur?: boolean,
 *   leavePlayerTime?: number,
 *   closePlayVideoFocus?: boolean,
 *   closePauseVideoFocus?: boolean,
 *   closeFocusVideoFocus?: boolean,
 *   closeControlsBlur?: boolean,
 *   topBarAutoHide?: boolean,
 *   videoAttributes?: { [propName: string]: any },
 *   startTime?: number,
 *   seekedStatus?: 'play' | 'pause' | 'auto',
 *   miniprogress?: boolean,
 *   disableSwipeHandler?: () => any,
 *   enableSwipeHandler?: () => any,
 *   preProcessUrl?: (url: IUrl, ext?: { [propName: string]: any }) => { url: IUrl, [propName: string]: any },
 *   ignores?: Array<'cssfullscreen' | 'screenshot' | 'pip' | 'miniscreen' | 'keyboard' | 'download' | 'playbackrate' | 'time' | 'definition' | 'error' | 'fullscreen' | 'loading' | 'mobile' | 'pc' | 'play' | 'poster' | 'progress' | 'replay' | 'start' | 'volume' | string>,
 *   inactive?: number,
 *   lang?: string,
 *   controls?: boolean | { [propName: string]: any },
 *   marginControls?: boolean,
 *   fullscreenTarget?: HTMLElement, // 全屏作用的dom元素
 *   screenShot?: boolean | { [propName: string]: any },
 *   rotate?: boolean | { [propName: string]: any },
 *   pip?: boolean | { [propName: string]: any },
 *   download?: boolean | { [propName: string]: any },
 *   mini?: boolean | { [propName: string]: any },
 *   cssFullscreen?: boolean | { [propName: string]: any },
 *   keyShortcut?: boolean,
 *   presets?: any[],
 *   plugins?: any[]
 *   playbackRate?: boolean | Array<number> | { [propName: string]: any },
 *   definition?: { list: Array<IDefinition> , defaultDefinition?: IDefinition['definition'], [propName: string]: any},
 *   playsinline?: boolean,
 *   customDuration?: number,
 *   timeOffset?: number,
 *   icons?: { [propName: string]: string | HTMLElement | () => HTMLElement | string },
 *   i18n?: Array<any>,
 *   tabindex?: number,
 *   thumbnail?: {
 *     urls: Array<string>,
 *     pic_num: number,
 *     col: number,
 *     row: number,
 *     height?: number,
 *     width?: number,
 *   },
 *   videoConfig?: { [propName: string]: any },
 *   isHideTips?: boolean,
 *   minWaitDelay?: number,
 *   commonStyle?: {
 *     progressColor?: string,
 *     playedColor?: string,
 *     cachedColor?: string,
 *     sliderBtnStyle?: { [propName: string]: any },
 *     volumeColor?: string
 *   },
 *   remainMediaAfterDestroy?: boolean,
 *   [propName: string]: any;
 * } } IPlayerOptions
 */
/**
 * @returns { IPlayerOptions }
 */
export default function getDefaultConfig(): IPlayerOptions;
export type IUrl = string | MediaStream | {
    [propName: string]: any;
    src: string;
    type?: string;
}[];
export type IDefinition = {
    [propName: string]: any;
    url: IUrl;
    definition: any;
    bitrate?: number;
    bandwidth?: number;
    text?: string | {
        [propName: string]: any;
    };
    iconText?: string | {
        [propName: string]: any;
    };
};
export type IPlayerOptions = {
    [propName: string]: any;
    id?: string;
    el?: HTMLElement;
    mediaEl?: HTMLMediaElement | ((mediaConfig: {
        [propName: string]: any;
    }) => HTMLMediaElement);
    url?: IUrl;
    domEventType?: 'default' | 'touch' | 'mouse';
    nullUrlStart?: boolean;
    width?: number | string;
    height?: number | string;
    fluid?: boolean;
    fitVideoSize?: 'fixWidth' | 'fixHeight' | 'fixed';
    videoFillMode?: 'auto' | 'fillHeight' | 'fillWidth' | 'fill' | 'cover' | 'contain';
    volume?: number | {
        [propName: string]: any;
    };
    autoplay?: boolean;
    autoplayMuted?: boolean;
    loop?: boolean;
    isLive?: boolean;
    zoom?: number;
    videoInit?: boolean;
    poster?: string | {
        [propName: string]: any;
    };
    isMobileSimulateMode?: 'mobile' | 'pc';
    defaultPlaybackRate?: number;
    execBeforePluginsCall?: () => any;
    allowSeekAfterEnded?: boolean;
    enableContextmenu?: boolean;
    closeVideoClick?: boolean;
    closeVideoDblclick?: boolean;
    closePlayerBlur?: boolean;
    closeDelayBlur?: boolean;
    leavePlayerTime?: number;
    closePlayVideoFocus?: boolean;
    closePauseVideoFocus?: boolean;
    closeFocusVideoFocus?: boolean;
    closeControlsBlur?: boolean;
    topBarAutoHide?: boolean;
    videoAttributes?: {
        [propName: string]: any;
    };
    startTime?: number;
    seekedStatus?: 'play' | 'pause' | 'auto';
    miniprogress?: boolean;
    disableSwipeHandler?: () => any;
    enableSwipeHandler?: () => any;
    preProcessUrl?: (url: IUrl, ext?: {
        [propName: string]: any;
    }) => {
        [propName: string]: any;
        url: IUrl;
    };
    ignores?: Array<'cssfullscreen' | 'screenshot' | 'pip' | 'miniscreen' | 'keyboard' | 'download' | 'playbackrate' | 'time' | 'definition' | 'error' | 'fullscreen' | 'loading' | 'mobile' | 'pc' | 'play' | 'poster' | 'progress' | 'replay' | 'start' | 'volume' | string>;
    inactive?: number;
    lang?: string;
    controls?: boolean | {
        [propName: string]: any;
    };
    marginControls?: boolean;
    fullscreenTarget?: HTMLElement;
    screenShot?: boolean | {
        [propName: string]: any;
    };
    rotate?: boolean | {
        [propName: string]: any;
    };
    pip?: boolean | {
        [propName: string]: any;
    };
    download?: boolean | {
        [propName: string]: any;
    };
    mini?: boolean | {
        [propName: string]: any;
    };
    cssFullscreen?: boolean | {
        [propName: string]: any;
    };
    keyShortcut?: boolean;
    presets?: any[];
    plugins?: any[];
    playbackRate?: boolean | number[] | {
        [propName: string]: any;
    };
    definition?: {
        [propName: string]: any;
        list: Array<IDefinition>;
        defaultDefinition?: IDefinition['definition'];
    };
    playsinline?: boolean;
    customDuration?: number;
    timeOffset?: number;
    icons?: {
        [propName: string]: string | HTMLElement | (() => HTMLElement | string);
    };
    i18n?: Array<any>;
    tabindex?: number;
    thumbnail?: {
        urls: Array<string>;
        pic_num: number;
        col: number;
        row: number;
        height?: number;
        width?: number;
    };
    videoConfig?: {
        [propName: string]: any;
    };
    isHideTips?: boolean;
    minWaitDelay?: number;
    commonStyle?: {
        progressColor?: string;
        playedColor?: string;
        cachedColor?: string;
        sliderBtnStyle?: {
            [propName: string]: any;
        };
        volumeColor?: string;
    };
    remainMediaAfterDestroy?: boolean;
};
