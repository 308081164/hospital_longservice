declare class GapJump extends BasePlugin {
    static get defaultConfig(): {
        useGapJump: boolean;
        smallGapLimit: number;
        gapDetectionThreshold: number;
    };
    hasPlayed: boolean;
    seekingEventReceived: boolean;
    isSafari: boolean;
    onGapJump: () => void;
    _getIndex(buffered: any, time: any, threshold: any): number;
    _getBuffered(b: any): {
        start: any;
        end: any;
    }[];
}
declare namespace GapJump {
    const BROWSER_GAP_TOLERANCE: number;
}
export default GapJump;
import BasePlugin from "../../plugin";
