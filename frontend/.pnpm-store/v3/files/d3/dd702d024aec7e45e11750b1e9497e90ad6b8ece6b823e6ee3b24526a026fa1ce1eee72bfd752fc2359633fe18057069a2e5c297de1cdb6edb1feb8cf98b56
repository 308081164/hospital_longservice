export default MediaProxy;
export type EventEmitter = import('eventemitter3');
export type IMediaProxy = {
    duration: number;
    currentTime: number;
    muted: boolean;
    defaultMuted: boolean;
    volume: number;
    playbackRate: number;
    defaultPlaybackRate: number;
    autoplay: boolean;
    readonly paused: boolean;
    readonly ended: boolean;
    readonly networkState: number;
    readonly readyState: number;
    readonly seeking: boolean;
    src: any;
    play: Function;
    pause: Function;
};
export type IPlayerOptions = import('./defaultConfig').IPlayerOptions;
/**
 * @extends { EventEmitter }
 */
declare class MediaProxy {
    /**
     * @param { IPlayerOptions } options
     */
    constructor(options: IPlayerOptions);
    /**
     * @type { boolean }
     * @private
     */
    private _hasStart;
    /**
     * @type { number }
     * @private
     */
    private _currentTime;
    /**
     * @type { number }
     * @private
     */
    private _duration;
    /**
     * @type {{[propName: string]: any}}
     * @private
     */
    private _internalOp;
    /**
     * @type { boolean }
     * @private
     */
    private _lastMuted;
    /**
     * @type {string}
     * 当前播放类型
     */
    vtype: string;
    /**
     * @type {number}
     * @private
     */
    private _rate;
    /**
     * @description 初始化时添加在video上的属性集合
     * @type { {[propName: string]: any } }
     */
    mediaConfig: {
        [propName: string]: any;
    };
    /**
     * @type { HTMLVideoElement | HTMLAudioElement | HTMLElement | IMediaProxy | null }
     */
    media: HTMLVideoElement | HTMLAudioElement | HTMLElement | IMediaProxy | null;
    set volume(arg: number);
    /**
     * @type { number }
     * @description 设置/返回视频的音量
     */
    get volume(): number;
    /**
     * @private
     */
    private _interval;
    /**
     * @readonly
     */
    readonly mediaEventMiddleware: {};
    /**
     * @description set middleware
     * @param { {[propName: string]: (e: {player: any, eventName: string}, callback: () => void) => any} } middlewares
     */
    setEventsMiddleware(middlewares: {
        [propName: string]: (e: {
            player: any;
            eventName: string;
        }, callback: () => void) => any;
    }): void;
    /**
     * @description remove middleware
     * @param { { [propName: string]: (e: {player: any, eventName: string}, callback: () => void) => any} } middlewares
     */
    removeEventsMiddleware(middlewares: {
        [propName: string]: (e: {
            player: any;
            eventName: string;
        }, callback: () => void) => any;
    }): void;
    /**
     * Add media eventListener to the video object
     * @param { any } [media]
     */
    attachVideoEvents(media?: any): void;
    /**
     * @private
     */
    private _evHandlers;
    /**
     * @description remove media eventListener from the video object
     * @param { any } [media]
     */
    detachVideoEvents(media?: any): void;
    /**
     * 针对source列表播放方式添加错误监听
     * @doc https://stackoverflow.com/questions/47557135/html5-detect-the-type-of-error-when-trying-to-load-a-video-using-a-source-elem
     * @protected
     * @param { HTMLVideoElement | HTMLAudioElement } video
     * @param { Array<{src: string, type: string }>} urls
     */
    protected _attachSourceEvents(video: HTMLVideoElement | HTMLAudioElement, urls: Array<{
        src: string;
        type: string;
    }>): void;
    /**
     * @private
     */
    private _videoSourceCount;
    _videoSourceIndex: number;
    _vLoadeddata: (e: any) => void;
    _sourceError: (e: any) => void;
    /**
     * 移除source列表错误事件监听
     * @protected
     * @param { HTMLVideoElement | HTMLAudioElement } video
     */
    protected _detachSourceEvents(video: HTMLVideoElement | HTMLAudioElement): void;
    /**
     * @description Media Error handler
     * @param { string } eventName
     */
    errorHandler(name: any, error?: any): void;
    destroy(): void;
    /**
     * @type { HTMLVideoElement | HTMLAudioElement | HTMLElement | IMediaProxy | null }
     * @deprecated Property [video] is renamed to [media],you can access using player.media= xx
     */
    set video(arg: HTMLElement | HTMLVideoElement | HTMLAudioElement | IMediaProxy);
    /**
     * @type { HTMLVideoElement | HTMLAudioElement | HTMLElement | IMediaProxy | null }
     * @deprecated Property [video] is renamed to [media],you can access using player.media
     */
    get video(): HTMLElement | HTMLVideoElement | HTMLAudioElement | IMediaProxy;
    /**
     *
     * @returns {  Promise<void> | null }
     */
    play(): Promise<void> | null;
    pause(): void;
    load(): void;
    /**
     *
     * @param { string } type
     * @returns { boolean }
     */
    canPlayType(type: string): boolean;
    /**
     *
     * @param { any } [buffered]
     * @returns { Array<number> }
     */
    getBufferedRange(buffered?: any): Array<number>;
    /**
     * @type { boolean }
     * @description 设置/返回 自动播放属性
     */
    set autoplay(arg: any);
    get autoplay(): any;
    /**
     * @type { TimeRanges | null }
     * @description  返回当前缓冲的TimeRange对象集合
     */
    get buffered(): TimeRanges;
    /**
     * @type { Array<{start: number, end: number}> | null}
     * @description  返回当前自定义的缓存列表
     */
    get buffered2(): {
        start: number;
        end: number;
    }[];
    /**
     * @type { {start: number, end: number} }
     */
    get bufferedPoint(): {
        start: number;
        end: number;
    };
    set crossOrigin(arg: string);
    /**
     * @type { string}
     * @description 设置/返回是否跨域
     * */
    get crossOrigin(): string;
    set currentSrc(arg: string);
    /**
     * @type { string }
     * @description 设置/返回视频播放地址
     * */
    get currentSrc(): string;
    set currentTime(arg: number);
    /**
     * @type { number }
     * @description 设置/返回视频当前播放时间
     * */
    get currentTime(): number;
    set defaultMuted(arg: boolean);
    /**
     * @type { boolean }
     * 设置/返回视频默认静音
     * */
    get defaultMuted(): boolean;
    /**
     * @type { number }
     * @description 返回视频时长，单位：s
     * */
    get duration(): number;
    /**
     * @type { boolean }
     * @description  回视频是否播放结束
     * */
    get ended(): boolean;
    /**
     * @type { MediaError }
     * @description the player current error
     */
    get error(): MediaError;
    /**
     * @type { string }
     * @description return error description text
     */
    get errorNote(): string;
    set loop(arg: boolean);
    /**
     * @type { boolean }
     * @description 否开启了循环播放
     */
    get loop(): boolean;
    set muted(arg: boolean);
    /**
     * @type { boolean }
     * @description 静音
     */
    get muted(): boolean;
    /**
     * @type { 0 | 1 | 2 | 3 }
     * @description  返回视频的当前网络状态
     */
    get networkState(): 0 | 2 | 1 | 3;
    /**
     * @type { boolean }
     * @description  回当前视频是否是暂停状态
     */
    get paused(): boolean;
    set playbackRate(arg: number);
    /**
     * @type { number }
     * @description 返回/设置倍速
     */
    get playbackRate(): number;
    /**
     * @type { TimeRanges | null}
     */
    get played(): TimeRanges;
    set preload(arg: boolean);
    /**
     * @type { boolean }
     */
    get preload(): boolean;
    /**
     * @type { 0 | 1 | 2 | 3 | 4 }
     * @description 回视频的就绪状态
     */
    get readyState(): 0 | 2 | 1 | 3 | 4;
    /**
     * @type { boolean }
     * @description 当前视频是否可以seek
     */
    get seekable(): boolean;
    /**
     * @type { boolean }
     * @description 当前视频是否处于seeking状态下
     */
    get seeking(): boolean;
    set src(arg: any);
    /**
     * @type { any }
     * @description 设置/返回当前视频的地址
     */
    get src(): any;
    /**
     * @type { number }
     * @description 返回视频的纵横比
     */
    get aspectRatio(): number;
    addInnerOP(event: any): void;
    removeInnerOP(event: any): void;
    /** ******************* 以下api只有申明作用,具体实现依赖EventEmitter ******************/
    /**
     * @param { string } event
     * @param { any } [data]
     * @returns
     */
    emit(event: string, data?: any, ...args: any[]): void;
    /**
     * @param { string } event
     * @param { (...args: any[]) => any } callback
     * @returns
     */
    on(event: string, callback: (...args: any[]) => any, ...args: any[]): void;
    /**
     * @param { string } event
     * @param { (...args: any[]) => any } callback
     * @returns
     */
    once(event: string, callback: (...args: any[]) => any, ...args: any[]): void;
    /**
     *
     * @param { string } event
     * @param { (...args: any[]) => any } callback
     * @returns
     */
    off(event: string, callback: (...args: any[]) => any, ...args: any[]): void;
    offAll(): void;
}
