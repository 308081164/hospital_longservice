export default class Stats extends BasePlugin {
    static get defaultConfig(): {};
    _recordUserActions: (actionData: any) => void;
    _getTime(): {
        timestamp: number;
        timeFormat: string;
    };
    _onReset: () => void;
    _recordInfo: (data: any) => void;
    _downloadStats: () => void;
    downloadStats(): void;
    /**
     * @params type
     */
    info(data: any): void;
    _info(data: any): void;
    _infoProfile(data: any): void;
    reset(): void;
    _stats: {
        info: any[];
        media: {};
    };
    getStats(): {
        raw: {
            info: any[];
            media: {};
        };
        timestat: string[];
        profile: {
            traceEvents: any[];
        };
    };
    _getTimeStats(): string[];
    _getProfile(): {
        traceEvents: any[];
    };
}
import { BasePlugin } from "../../plugin";
