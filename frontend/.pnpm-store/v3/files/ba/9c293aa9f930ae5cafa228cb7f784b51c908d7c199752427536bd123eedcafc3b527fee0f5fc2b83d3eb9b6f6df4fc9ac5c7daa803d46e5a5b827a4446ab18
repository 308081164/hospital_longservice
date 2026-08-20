export default class WaitingTimeoutJump extends BasePlugin {
    static get defaultConfig(): {
        useWaitingTimeoutJump: boolean;
        waitingTime: number;
        jumpSize: number;
        jumpCntMax: number;
    };
    hasPlayed: boolean;
    jumpCnt: number;
    /**
     * @type {null | number}
     */
    timer: null | number;
    jumpSize: any;
    onWaiting: () => void;
    onJump: () => void;
}
import BasePlugin from "../../plugin";
