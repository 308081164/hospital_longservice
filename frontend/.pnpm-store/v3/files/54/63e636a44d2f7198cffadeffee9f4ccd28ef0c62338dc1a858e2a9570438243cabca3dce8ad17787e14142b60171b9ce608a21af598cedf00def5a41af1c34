/**
 * 进行事件分段控制
 */
export default class TimeSegmentsControls extends BasePlugin {
    static get defaultConfig(): {
        disable: boolean;
        segments: any[];
    };
    curIndex: any;
    curPos: any;
    lastCurrentTime: any;
    setConfig(newConfig: any): void;
    updateSegments(): void;
    formatTimeSegments(timeSegments: any, duration: any): any[];
    _onDurationChange: () => void;
    _onLoadedData: () => void;
    _onTimeupdate: () => void;
    _onSeeking: () => void;
    _onPlay: () => void;
    getSeekTime(currentTime: any, lastCurrentTime: any, index: any, timeSegments: any): number;
    _checkIfEnabled(segments: any): boolean;
    changeIndex(index: any, timeSegments: any): void;
}
import { BasePlugin } from "../../plugin";
