import { BaseClass, DataCache } from './base/index';
import Control from './control';
import Main from './main';
import { CommentData, Configure, GlobalHooks } from './types/globals';
import { RecyclableNodeFactory } from './utils/index';
export declare class DanmuJs extends BaseClass {
    logger: any;
    config: Configure;
    gh: GlobalHooks;
    hideArr: any[];
    recycler: RecyclableNodeFactory;
    cache: DataCache;
    freezeId: string;
    container: Configure['container'];
    live: boolean;
    player: any;
    direction: string;
    main: Main;
    bulletBtn: Control;
    isReady: boolean;
    opacity: string;
    mouseControl: boolean;
    private fontSize;
    constructor(options: Configure);
    emit(...args: any): void;
    get status(): "idle" | "paused" | "playing" | "closed" | "destroy";
    get state(): {
        status: "idle" | "paused" | "playing" | "closed";
        comments: CommentData[];
        bullets: import("./bullet").Bullet[];
        displayArea: {
            width: number;
            height: number;
        };
    };
    get containerPos(): any;
    hooks(options: GlobalHooks): void;
    addResizeObserver(): void;
    start(): void;
    pause(): void;
    play(): void;
    stop(): void;
    clear(): void;
    destroy(): void;
    sendComment(comment: CommentData): void;
    setCommentID(oldID: string, newID: string): void;
    setCommentDuration(id: string, duration: number): void;
    setCommentLike(id: string, like: any): void;
    restartComment(id: string): void;
    private _releaseCtrl;
    private _freezeCtrl;
    freezeComment(id: string): void;
    removeComment(id: string): void;
    updateComments(comments: Array<CommentData>, isClear?: boolean): void;
    /**
     * 设置所有弹幕播放时长
     */
    setAllDuration(mode: string, duration: number, force?: boolean): void;
    /**
     * 设置弹幕播放速率，在弹幕播放速度上乘以一个系数，控制速度的变化
     */
    setPlayRate(mode: string, val: number): void;
    setOpacity(opacity: string): void;
    setFontSize(size: any, channelSize: any, options?: {
        reflow: boolean;
    }): void;
    setArea(area: any): void;
    hide(mode?: string): void;
    show(mode?: string): void;
    setDirection(direction?: Configure['direction']): void;
    resize(): void;
}
export default DanmuJs;
