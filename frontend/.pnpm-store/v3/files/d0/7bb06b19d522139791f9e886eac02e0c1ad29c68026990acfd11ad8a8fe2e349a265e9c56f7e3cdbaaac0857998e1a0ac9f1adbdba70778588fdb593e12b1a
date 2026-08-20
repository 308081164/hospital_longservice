import DanmuJs from '.';
import { BaseClass } from './base/index';
import { CommentData, CommentDataMode } from './types/globals';
/**
 * [Bullet 弹幕构造类]
 *
 * @description
 *  1. 几乎所有事件都可以被代理（包括CSS事件），Bullet内不要进行事件绑定。所有事件绑定均在main层处理
 *  2. 一个Bullet实例占据1K字节内存，应尽量避免在实例上增加属性，通用配置应放在弹幕实例或其它不会频繁实例化的对象上
 */
export declare class Bullet extends BaseClass {
    id: string;
    status: 'waiting' | 'start' | 'end' | 'paused' | 'forcedPause';
    mode: CommentDataMode;
    el: HTMLElement;
    top?: number;
    prior: CommentData['prior'];
    start: CommentData['start'];
    duration?: CommentData['duration'];
    color?: CommentData['color'];
    options: CommentData;
    width?: number;
    height?: number;
    random?: number;
    channel_id: [number, number];
    bookChannelId?: CommentData['bookChannelId'];
    private danmu;
    private container;
    private elPos?;
    private startTime?;
    private pastDuration?;
    private _moveV?;
    private hasMove?;
    private moveMoreS?;
    private moveContainerHeight?;
    private moveContainerWidth?;
    private _fullySlideInScreenDuration;
    private _lastMoveTime;
    private reuseDOM?;
    private noCopyEl;
    constructor(danmu: DanmuJs);
    init(options: CommentData): Promise<boolean>;
    destroy(): void;
    get moveV(): number;
    get direction(): string;
    get fullySlideIntoScreen(): boolean;
    _makeEl(): Promise<boolean>;
    updateOffset(val: any, dryRun?: boolean): string;
    attach(): Promise<void>;
    detach(): void;
    topInit(): void;
    pauseMove(isFullscreen?: boolean): void;
    startMove(force?: boolean): void;
    remove(needPause?: boolean): void;
    setFontSize(size: any): void;
    setLikeDom(el: any, style: any): any;
}
export default Bullet;
