export default class TestSpeed extends BasePlugin {
    static get defaultConfig(): {
        openSpeed: boolean;
        testCnt: number;
        loadSize: number;
        testTimeStep: number;
        url: string;
        saveSpeedMax: number;
        addSpeedTypeList: any[];
    };
    getSpeed: (type?: string) => number;
    /**
     * @type {null || number }
     */
    timer: any;
    cnt: number;
    xhr: XMLHttpRequest;
    startTimer: () => void;
    initSpeedList: () => void;
    speedListCache: {};
    _onRealSpeedChange: (data: any) => void;
    testSpeed: () => void;
    appendList: (speed: any, type?: string) => void;
    getSpeedName(namePrefix: any, type: any): string;
    updateSpeed: (type?: string) => void;
    set openSpeed(arg: any);
    get openSpeed(): any;
}
import BasePlugin from "../../plugin";
