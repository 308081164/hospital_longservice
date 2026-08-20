import { BaseClass } from './base/index';
import Bullet from './bullet';
import Channel from './channel';
import DanmuJs from './danmu';
import { CommentData } from './types/globals';
/**
 * [Main 弹幕主进程]
 */
declare class Main extends BaseClass {
    danmu: DanmuJs;
    channel: Channel;
    data: CommentData[];
    playedData: CommentData[];
    queue: Bullet[];
    playRate: number;
    forceDuration: number;
    private container;
    private retryStatus;
    private _status;
    private _events;
    private handleId;
    private handleTimer;
    constructor(danmu: any);
    get status(): "idle" | "paused" | "playing" | "closed";
    destroy(): void;
    private _bindEvents;
    private _unbindEvents;
    private _cancelDataHandleTimer;
    init(): void;
    start(): void;
    stop(): void;
    clear(): void;
    play(): void;
    pause(): void;
    handleQueue(): void;
    readData(): Promise<void>;
    sortData(): void;
    keepPoolWatermark(): void;
    private _getBulletByEvt;
}
export default Main;
