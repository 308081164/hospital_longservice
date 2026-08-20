/**
 * @typedef { {
 *   tick?: number,
 *   stuckCount?: number,
 *   disable?: boolean,
 *   reportFrame?: number
 * } } IFullscreenConfig
 */
export default class FpsDetect extends BasePlugin {
    static get defaultConfig(): {
        disabled: boolean;
        tick: number;
        stuckCount: number;
        reportFrame: number;
    };
    timer: any;
    _lastDecodedFrames: any;
    _currentStuckCount: number;
    _lastCheckPoint: number;
    _payload: any[];
    _startTick(): void;
    _timer: NodeJS.Timeout;
    _stopTick(): void;
    _checkBuffer(curTime: any, buffered: any): {
        enoughBuffer: boolean;
        buffers: {
            start: any;
            end: any;
        }[];
    };
    _checkStuck(curDecodedFrames: any, totalVideoFrames: any, droppedVideoFrames: any, checkInterval: any): void;
    _reset(): void;
    _checkDecodeFPS(): void;
}
export type IFullscreenConfig = {
    tick?: number;
    stuckCount?: number;
    disable?: boolean;
    reportFrame?: number;
};
import BasePlugin from "../../plugin";
