export namespace LOG_TYPES {
    const LOAD_START: string;
    const LOADED_DATA: string;
    const FIRST_FRAME: string;
    const WAIT_START: string;
    const WAIT_END: string;
    const SEEK_START: string;
    const SEEK_END: string;
}
declare class XGLogger extends BasePlugin {
    static get defaultConfig(): {
        waitTimeout: number;
    };
    _waitType: string | number;
    newPointTime: number;
    loadedCostTime: number;
    startCostTime: number;
    pt: number;
    vt: number;
    fvt: number;
    _initOnceEvents(): void;
    _sendFF(endType: any): void;
    s: number;
    _onTimeupdate: () => void;
    _onAutoplayStart: () => void;
    _onReset: () => void;
    _state: {
        autoplayStart: boolean;
        isFFLoading: boolean;
        isTimeUpdate: boolean;
        isFFSend: boolean;
        isLs: boolean;
    };
    _isSeeking: boolean;
    seekingStart: number;
    waitingStart: number;
    fixedWaitingStart: number;
    _isWaiting: boolean;
    _waitTimer: any;
    _waittTimer: any;
    _onSeeking: () => void;
    _onSeeked: () => void;
    _onWaitingLoadStart: () => void;
    _onWaiting: () => void;
    _onError: () => void;
    _startWaitTimeout(): void;
    _onPlaying: () => void;
    endState(endType: any): void;
    suspendSeekingStatus(endType: any): void;
    suspendWaitingStatus(endType: any): void;
    emitLog(eventType: any, data: any): void;
}
import BasePlugin from "../../plugin";
export { XGLogger as default };
