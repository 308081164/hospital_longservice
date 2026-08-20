export type IPlayerOptions = import('./defaultConfig').IPlayerOptions;
export type IDefinition = import('./defaultConfig').IDefinition;
export type IUrl = import('./defaultConfig').IUrl;
export type SwitchUrlOptions = boolean | {
    seamless?: boolean;
    currentTime?: number;
    bitrate?: number;
};
declare class Player extends MediaProxy {
    /**
     * @type {number}
     * @description set debugger level
     *  1 - only print error logs
     *  2 - print warn logs and error logs
     *  3 - print all debug logs and error stack logs
     */
    static set debugger(arg: number);
    static get debugger(): number;
    /**
     * @param {import('./instManager').InstManager} value
     */
    static set instManager(arg: InstManager);
    /**
     * @param {import('./instManager').InstManager} value
     */
    static get instManager(): InstManager;
    /**
     * 获取当前处理激活态的实例id
     * @returns { number | string | null }
     * @deprecated 该方法转移到 InstManager 上去使用: player.instManager.getActiveId()
     */
    static getCurrentUserActivePlayerId(): number | string | null;
    /**
     * 设置实例的用户行为激活状态
     * @param { number | string } playerId
     * @param { boolean } isActive
     * @deprecated 该方法转移到 InstManager 上去使用: player.instManager.setActive()
     */
    static setCurrentUserActive(playerId: number | string, isActive: boolean): void;
    /**
     * 当前浏览器是否支持hevc编码
     * @returns {boolean}
     */
    static isHevcSupported(): boolean;
    /**
     * 检测编码参数是否被当前浏览器支持
     * @doc https://developer.mozilla.org/en-US/docs/Web/API/MediaCapabilities/decodingInfo
     * @param {MediaDecodingConfiguration} info
     * @returns {MediaCapabilitiesDecodingInfo}
     */
    static probeConfigSupported(info: MediaDecodingConfiguration): MediaCapabilitiesDecodingInfo;
    /**
     * @deprecated
     * 插件全部迁移完成再做删除
     */
    static install(name: any, descriptor: any): void;
    /**
     * @deprecated
     * 插件全部迁移完成再做删除
     */
    static use(name: any, descriptor: any): void;
    static defaultPreset: any;
    /**
     * @description 自定义media构造函数
     */
    static XgVideoProxy: any;
    /**
     * @type { IPlayerOptions }
     * @description 当前播放器的配置信息
     */
    config: IPlayerOptions;
    /**
     * @type { string }
     * @private
     */
    private _pluginInfoId;
    userTimer: any;
    /**
     * @private
     */
    private waitTimer;
    /**
     * @public
     * @description This switch provides the playback plugin with the ability
     *  to handle media source by itself.
     */
    public handleSource: boolean;
    /**
     * @private
     */
    private _state;
    /**
     * @public
     * @readonly
     * @type { boolean }
     */
    public readonly isAd: boolean;
    /**
     * @public
     * @readonly
     * @type { boolean }
     */
    public readonly isError: boolean;
    /**
     * Whether the player is in the seeking state
     * @type { boolean }
     * @readonly
     */
    readonly isSeeking: boolean;
    /**
     * @type { boolean }
     * @readonly
     */
    readonly isCanplay: boolean;
    /**
     * @private
     * @readonly
     */
    private readonly _useAutoplay;
    /**
     * @description 记录起播需要seek的时间点
     * @type { number }
     * @private
     * @readonly
     */
    private readonly __startTime;
    /**
     *  @type { number }
     */
    rotateDeg: number;
    /**
     * Whether the player is focus
     * @type { boolean }
     * @readonly
     */
    readonly isActive: boolean;
    /**
     * Whether player is currently in fullscreen
     * @type { boolean }
     * @readonly
     */
    readonly fullscreen: boolean;
    /**
     * @type { boolean }
     * @readonly
     */
    readonly cssfullscreen: boolean;
    /**
     * @type { boolean }
     * @readonly
     */
    readonly isRotateFullscreen: boolean;
    /**
     * fullscreenElement
     * @type { HTMLElement | null }
     * @readonly
     */
    readonly _fullscreenEl: HTMLElement | null;
    timeSegments: any[];
    /**
     * cssfullscreen target Element
     * @type { HTMLElement | null }
     * @readonly
     */
    readonly _cssfullscreenEl: HTMLElement | null;
    /**
     * @type { ?IDefinition }
     * @public
     */
    public curDefinition: IDefinition | null;
    /**
     * @private
     * @type { string }
     */
    private _orgCss;
    /**
     * @readonly
     * @type { number }
     */
    readonly _fullScreenOffset: number;
    /**
     * @private
     * @type { number }
     */
    private _videoHeight;
    /**
     * @private
     * @type { number }
     */
    private _videoWidth;
    videoPos: {
        pi: number;
        scale: number;
        rotate: number;
        x: number;
        y: number;
        h: number;
        w: number;
        vy: number;
        vx: number;
    };
    sizeInfo: {
        width: number;
        height: number;
        left: number;
        top: number;
    };
    /**
     * @private
     * @type { { t: number, acc:number, acc: number, loopAcc: number, [propName: string]: any;} }
     */
    private _accPlayed;
    /**
     * @description 播放器时长、播放时间偏移信息
     */
    _offsetInfo: {
        currentTime: number;
        duration: number;
    };
    /**
     * @type { null | HTMLElement }
     * @readonly
     * @description  控制栏和video不同布局的时候内部容器
     */
    readonly innerContainer: null | HTMLElement;
    /**
     * @type { ?Controls }
     * @readonly
     * @description 控制栏插件
     */
    readonly controls: Controls | null;
    /**
     * @type { null | HTMLElement }
     * @readonly
     */
    readonly topBar: null | HTMLElement;
    /**
     * @type { null | HTMLElement }
     * @readonly
     * @description 当前播放器根节点
     */
    readonly root: null | HTMLElement;
    __i18n: import("./lang/i18n").IXGI18n;
    /**
     * @readonly
     * @type {any}
     */
    readonly database: any;
    /**
     * @readonly
     * @type { boolean }
     */
    readonly isUserActive: boolean;
    /**
     * @private
     * @description seek之后canplay回调函数定义
     * @type { Function | null }
     */
    private _onceSeekCanplay;
    /**
     * @description 记录seek之前是否处于暂停状态
     * @type { number }
     * @private
     */
    private _isPauseBeforeSeek;
    /**
     * @description 内部状态记录
     * @type { {
    *   isActiveLocked: boolean;
    * } }
    * @readonly
    */
    readonly innerStates: {
        isActiveLocked: boolean;
    };
    /**
     * @type {InstManager}
     */
    instManager: InstManager;
    /**
     * init control domElement
     * @private
     */
    private _initDOM;
    /**
     * @private
     */
    private _initBaseDoms;
    /**
     * @readonly
     * @type { HTMLElement |null }
     */
    readonly leftBar: HTMLElement | null;
    /**
     * @readonly
     * @type { HTMLElement | null }
     */
    readonly rightBar: HTMLElement | null;
    /**
     * @private
     */
    private _bindEvents;
    playFunc: () => void;
    /**
     * @private
     */
    private _unbindEvents;
    _clearUserTimer(): void;
    /**
     * @param { any } [url]
     * @param { { playSessionId?: number  } } [options]
     * @returns
     */
    _startInit(url?: any, options?: {
        playSessionId?: number;
    }): void;
    _playSessionId: number;
    set hasStart(arg: boolean);
    /**
     * @type { boolean }
     * @description 是否开始播放
     */
    get hasStart(): boolean;
    /**
     * @description 注册组件 组件列表config.plugins
     * @param { {boolean} } [isInit] 是否是首次初始化
     * @private
     */
    private _registerPlugins;
    /**
     * @private
     */
    private _loadingPlugins;
    /**
     * @private
     */
    private _registerPresets;
    /**
     * @private
     * @param { string } position ]
     */
    private _getRootByPosition;
    /**
     *
     * @param { {plugin: function, options:object} | function } plugin
     * @param { {[propName: string]: any;} } [config]
     * @returns { any } plugin
     */
    registerPlugin(plugin: Function | {
        plugin: Function;
        options: object;
    }, config?: {
        [propName: string]: any;
    }): any;
    /**
     *
     * @param { any } plugin
     */
    deregister(plugin: any): void;
    /**
     *
     * @param { any } plugin
     * @param { boolean } removedFromConfig
     */
    unRegisterPlugin(plugin: any, removedFromConfig?: boolean): void;
    removePluginFromConfig(plugin: any): void;
    /**
     * 当前播放器挂载的插件实例列表
     * @type { {[propName: string]: any | null } }
     */
    get plugins(): {
        [propName: string]: any;
    };
    /**
     * get a plugin instance
     * @param { string } pluginName
     * @return { null | any } plugin
     */
    getPlugin(pluginName: string): null | any;
    /**
     *
     * @param { string } className
     */
    addClass(className: string): void;
    /**
     *
     * @param { string } className
     * @returns
     */
    removeClass(className: string): void;
    /**
     *
     * @param { string } className
     * @returns { boolean } has
     */
    hasClass(className: string): boolean;
    /**
     *
     * @param { string } key
     * @param { any } value
     * @returns void
     */
    setAttribute(key: string, value: any): void;
    /**
     *
     * @param { string } key
     * @param { any } value
     * @returns void
     */
    removeAttribute(key: string, value: any): void;
    /**
     *
     * @param { any } [url]
     * @returns { Promise<void> | void }
     * @description 启动播放器，start一般都是播放器内部隐式调用，主要功能是将video添加到DOM
     */
    start(url?: any): Promise<void> | void;
    /**
     * @param { string | object } url
     * @param { SwitchUrlOptions } [options]
     * @returns { Promise | null } 执行结果
     */
    switchURL(url: string | object, options?: SwitchUrlOptions): Promise<any> | null;
    /**
     * @description call play without play hook
     * @deprecated this api renamed to mediaPlay, you can call it as player.mediaPlay()
     */
    videoPlay(): void;
    /**
     * @description call play without play hook
     */
    mediaPlay(): Promise<void>;
    /**
     * @private
     */
    private _errorTimer;
    /**
     * @description call play without pause hook
     */
    mediaPause(): void;
    /**
     * @description call play without pause hook
     * @deprecated this api renamed to mediaPause, you can call it as player.mediaPause()
     */
    videoPause(): void;
    play(): any;
    /**
     *
     * @param { number } time
     * @param { 'play' | 'pause' | 'auto' } [status]
     * @returns
     */
    seek(time: number, status?: 'play' | 'pause' | 'auto'): void;
    currentTime: any;
    getInitDefinition(): void;
    /**
     * @typedef { import('./defaultConfig').IDefinition } definition
     */
    /**
     * @description change the definition of the current playback
     * @param { definition } to
     * @param { definition } [from]
     */
    changeDefinition(to: import("./defaultConfig").IDefinition, from?: import("./defaultConfig").IDefinition): void;
    reload(): void;
    /**
     * @private
     */
    private reloadFunc;
    resetState(): void;
    /**
     * 重置播放实例
     * @param { Array<string> } unregisterPlugins 重置的时候需要卸载重新初始化的插件列表
     * @param { boolean } [isResetConfig] 是否需要重置配置列表
     * @returns
     */
    reset(unregisterPlugins?: Array<string>, isResetConfig?: boolean): void;
    replay(): void;
    retry(): void;
    /**
     *
     * @param { HTMLElement } root
     * @param { HTMLElement } [el]
     * @param { string } [rootClass]
     * @param { string } [pClassName]
     */
    changeFullStyle(root: HTMLElement, el?: HTMLElement, rootClass?: string, pClassName?: string): void;
    /**
     * @private
     */
    private _orgPCss;
    /**
     *
     * @param { HTMLElement } root
     * @param { HTMLElement } [el]
     * @param { string } [rootClass]
     * @param { string } [pClassName]
     */
    recoverFullStyle(root: HTMLElement, el?: HTMLElement, rootClass?: string, pClassName?: string): void;
    /**
     * @param { HTMLElement } [el]
     * @returns { Promise<void> }
     */
    getFullscreen(el?: HTMLElement): Promise<void>;
    /**
     * @private
     */
    private _fullActionFrom;
    /**
     * @param { HTMLElement } [el]
     * @returns { Promise<void> }
     */
    exitFullscreen(el?: HTMLElement): Promise<void>;
    /**
     * @param { HTMLElement } [el]
     * @returns
     */
    getCssFullscreen(el?: HTMLElement): void;
    /**
     * @param { HTMLElement } [el]
     * @returns
     */
    exitCssFullscreen(): void;
    /**
     * 进入旋转全屏
     * @param { HTMLElement } [el]
     */
    getRotateFullscreen(el?: HTMLElement): void;
    _rootStyle: string | boolean;
    /**
     * 退出旋转全屏
     * @param { HTMLElement } [el]
     */
    exitRotateFullscreen(el?: HTMLElement): void;
    setRotateDeg(deg: any): void;
    /**
     * @description 播放器焦点状态，控制栏显示
     * @param { {
     *   autoHide?: boolean, // 是否可以自动隐藏
     *   delay?: number     // 自动隐藏的延迟时间, ms, 不传默认使用3000ms
     *   isLock?: boolean   // 是否锁定, 锁定之后自动呼出和blur调用都不生效, 默认值false
     * } } [data]
     */
    focus(data?: {
        autoHide?: boolean;
        delay?: number;
        isLock?: boolean;
    }): void;
    /**
     * @description 取消播放器当前焦点状态
     * @param { { ignorePaused?: boolean } } [data]
     */
    blur(data?: {
        ignorePaused?: boolean;
    }): void;
    /**
     * @protected
     * @param { { autoHide?: boolean, delay?: number, isLock?: boolean } } [data]
     * @returns
     */
    protected onFocus(data?: {
        autoHide?: boolean;
        delay?: number;
        isLock?: boolean;
    }): void;
    /**
     * @protected
     * @param {{ ignorePaused?: boolean }} [data]
     * @returns
     */
    protected onBlur({ ignorePaused }?: {
        ignorePaused?: boolean;
    }): void;
    canPlayFunc: () => void;
    /**
     *
     */
    onFullscreenChange: (event: any, isFullScreen: any) => void;
    _onWebkitbeginfullscreen: (e: any) => void;
    _onWebkitendfullscreen: (e: any) => void;
    onEmptied(): void;
    /**
     * @protected
     */
    protected onCanplay(): void;
    onLoadeddata(): void;
    onLoadstart(): void;
    /**
     * @protected
     */
    protected onPlay(): void;
    /**
     * @protected
     */
    protected onPause(): void;
    /**
     * @protected
     */
    protected onEnded(): void;
    /**
     * @protected
     */
    protected onError(): void;
    /**
     * @protected
     */
    protected onSeeking(): void;
    /**
     * @protected
     */
    protected onSeeked(): void;
    /**
     * @protected
     */
    protected onWaiting(): void;
    /**
     * @protected
     */
    protected onPlaying(): void;
    /**
     * @protected
     */
    protected onTimeupdate(): void;
    onVolumechange(): void;
    onRatechange(): void;
    /**
     * 触发用户行为事件，第一个参数是Dom事件
     * @param { Event } event
     * @param { string } action
     * @param {[propName: string]: any; } [params]
     * @returns
     */
    emitUserAction(event: Event, action: string, params: any): void;
    updateAcc(endType: any): void;
    /**
     * 目标时间点是否在buffer内
     * @param { number } time 时间点
     * @param options 判断阈值配置，可选的
     * @param options.startDiff 判断起始阈值，即该时间下距离buffer开始点超过阈值时才算作在buffer内，默认为0
     * @param options.endDiff 判断结束阈值，即该时间下距离buffer结束点超过阈值时才算作在buffer内，默认为0
     * @returns { boolean }
     */
    checkBuffer(time: number, options?: {
        startDiff: number;
        endDiff: number;
    }): boolean;
    resizePosition(): void;
    /**
     * @description position video/audio according to height ratio and y coordinate
     * @param { { h: number, y?: number, x?:number, w?:number} } pos
     * @returns
     */
    position(pos?: {
        h: number;
        y?: number;
        x?: number;
        w?: number;
    }): void;
    /**
     * @description Update configuration parameters
     * @param { IPlayerOptions } config
     */
    setConfig(config: IPlayerOptions): void;
    /**
     * @description play another video resource
     * @param { IPlayerOptions } config
     */
    playNext(config: IPlayerOptions): void;
    resize(): void;
    /**
     *
     * @param { number } left
     * @param { number } top
     * @returns
     */
    updateObjectPosition(left?: number, top?: number): void;
    /**
     * @protected
     * @param { number } newState
     */
    protected setState(newState: number): void;
    /**
     * @description url preprocessing
     * @param { IUrl } url
     * @param { {[propName: string]: any} } [ext]
     * @returns { url: IUrl, [propName: string]: any }
     * @public
     */
    public preProcessUrl(url: IUrl, ext?: {
        [propName: string]: any;
    }): import("./defaultConfig").IUrl;
    /**
     * @description 跳转至配置的起播时间点
     */
    _seekToStartTime(): void;
    /**
     * @type { number }
     */
    get state(): number;
    /**
     * @type { boolean }
     */
    get isFullscreen(): boolean;
    /**
     * @type { boolean }
     */
    get isCssfullScreen(): boolean;
    set isPlaying(arg: boolean);
    /**
     * @type { boolean }
     * @description 是否已经进入起播状态
     */
    get isPlaying(): boolean;
    /**
     * @type { Array.<IDefinition> }
     */
    set definitionList(arg: import("./defaultConfig").IDefinition[]);
    get definitionList(): import("./defaultConfig").IDefinition[];
    /**
     * @description VideoFrames infos
     * @type { {
     *   total: number,
     *   dropped: number,
     *   corrupted: number,
     *   droppedRate: number,
     *   droppedDuration: number
     * } }
     */
    get videoFrameInfo(): {
        total: number;
        dropped: number;
        corrupted: number;
        droppedRate: number;
        droppedDuration: number;
    };
    /**
     * @type { string }
     */
    set lang(arg: string);
    get lang(): string;
    get i18n(): import("./lang/i18n").IXGI18nText;
    get i18nKeys(): {
        [propName: string]: string;
    };
    /**
     * @type { string }
     */
    get version(): string;
    /**
     * @type { number | string }
     */
    get playerId(): string | number;
    /**
     * @type { any }
     */
    set url(arg: any);
    get url(): any;
    /**
     * @private
     */
    private __url;
    /**
     * @type { string }
     */
    set poster(arg: any);
    get poster(): any;
    get error(): any;
    /**
     * @type { boolean }
     */
    get fullscreenChanging(): boolean;
    /**
     * 累计观看时长
     * @type { number }
     */
    get cumulateTime(): number;
    /**
     * @type { number }
     */
    set zoom(arg: number);
    /**
     * @type { number }
     */
    get zoom(): number;
    /**
     * @type {number}
     * @description Media element rotation angle, Only multiples of 90 degrees are supported
     */
    set videoRotateDeg(arg: number);
    get videoRotateDeg(): number;
    /**
     * @description 均衡下载速度，单位kb/s, 根据10条最新下载速度计算出来的加权值，如果没有测速能力则默认是0
     * @type { number }
     */
    set avgSpeed(arg: number);
    get avgSpeed(): number;
    /**
     * @type { number }
     * 最新一次下载速度，单位kb/s, 如果没有测速能力则默认是0
     */
    set realTimeSpeed(arg: number);
    get realTimeSpeed(): number;
    set offsetCurrentTime(arg: number);
    get offsetCurrentTime(): number;
    set offsetDuration(arg: number);
    get offsetDuration(): number;
    /**
     * @param { string } hookName
     * @param { Function } handler
     * @param { {pre: Function| null , next: Function | null} } preset
     * @returns
     */
    hook(hookName: string, handler: Function, preset?: {
        pre: Function | null;
        next: Function | null;
    }, ...args: any[]): any;
    /**
     * @param { string } hookName
     * @param { (player: any, ...args) => boolean | Promise<any> } handler
     * @param  {...any} args
     * @returns {boolean} isSuccess
     */
    useHooks(hookName: string, handler: (player: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): boolean;
    /**
     *
     * @param { string } hookName
     * @param { (player: any, ...args) => boolean | Promise<any> } handler
     * @returns
     */
    removeHooks(hookName: string, handler: (player: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): any;
    /**
     *
     * @param { string } pluginName
     * @param { string } hookName
     * @param { (plugin: any, ...args) => boolean | Promise<any> } handler
     * @param  {...any} args
     * @returns { boolean } isSuccess
     */
    usePluginHooks(pluginName: string, hookName: string, handler: (plugin: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): boolean;
    /**
     *
     * @param { string } pluginName
     * @param { string } hookName
     * @param { (plugin: any, ...args) => boolean | Promise<any> } handler
     * @param  {...any} args
     * @returns { boolean } isSuccess
     */
    removePluginHooks(pluginName: string, hookName: string, handler: (plugin: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): boolean;
    /**
     * 设置当前实例的用户激活态
     * @param { boolean } isActive
     * @param { boolean } [isMuted]
     */
    setUserActive(isActive: boolean, isMuted?: boolean): void;
}
import Plugin from "./plugin/plugin";
import BasePlugin from "./plugin/basePlugin";
import * as Events from "./events";
import Errors from "./error";
import Sniffer from "./utils/sniffer";
import Util from "./utils/util";
import STATE_CLASS from "./stateClassMap";
import I18N from "./lang/i18n";
import MediaProxy from "./mediaProxy";
import Controls from "./plugins/controls/index";
import { InstManager } from "./instManager";
export { Player as default, Plugin, BasePlugin, Events, Errors, Sniffer, Util, STATE_CLASS, I18N };
