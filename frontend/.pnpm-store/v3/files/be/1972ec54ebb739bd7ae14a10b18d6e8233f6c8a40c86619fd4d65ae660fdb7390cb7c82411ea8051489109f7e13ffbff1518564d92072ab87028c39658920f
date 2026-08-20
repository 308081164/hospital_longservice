import { BaseClass } from './base/index';
import Bullet from './bullet';
import DanmuJs from './danmu';
import { CommentDataMode, Configure } from './types/globals';
/**
 * [Channel 弹幕轨道控制]
 */
declare class Channel extends BaseClass {
    danmu: DanmuJs;
    channels: Array<{
        id: number;
        queue: {
            scroll: Array<Bullet>;
            top: Array<Bullet>;
            bottom: Array<Bullet>;
        };
        operating: {
            scroll: boolean;
            top: boolean;
            bottom: boolean;
        };
        bookId: {};
    }>;
    container: Configure['container'];
    resetId: any;
    width: number;
    height: number;
    channelCount: number;
    channelWidth: number;
    channelHeight: number;
    containerPos: any;
    containerWidth: number;
    containerHeight: number;
    containerTop: number;
    containerBottom: number;
    containerLeft: number;
    containerRight: number;
    resizing: boolean;
    resizeId: number;
    constructor(danmu: DanmuJs);
    get direction(): string;
    checkAvailableTrack(mode?: CommentDataMode): boolean;
    destroy(): void;
    reset(isInit?: boolean): void;
    getRealOccupyArea(): {
        width: number;
        height: number;
    };
    updatePos(): void;
    /**
     * Feature:
     * 1. 长弹幕速度较快，更容易出现追击问题，需要调整Offset
     * 2. 需要按实时性RealTime展示的弹幕，则不能再用duration来计算速度，这样会产生追击重叠问题
     */
    addBullet(bullet: Bullet): boolean;
    removeBullet(bullet: Bullet): void;
    resizeSync(): void;
    /**
     * @private
     */
    _initChannels(): {
        channelSize: number;
        channelCount: any;
        channels: any[];
    };
    resize(sync?: boolean): void;
    /**
     * @private
     */
    _cancelResizeTimer(): void;
}
export default Channel;
