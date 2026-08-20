export default Time;
declare class Time extends Plugin {
    static get defaultConfig(): {
        position: string;
        index: number;
        disable: boolean;
    };
    constructor(args: any);
    isActiving: boolean;
    get duration(): number;
    get currentTime(): any;
    get timeOffset(): number;
    mode: string;
    durationDom: HTMLElement;
    timeDom: HTMLElement;
    /**
     * This method can be overridden.
     * Eg. xgplayer-ads/ui/adTime.js
     */
    listenEvents(): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    onTimeUpdate(isEnded: any): void;
    onReset(): void;
    createCenterTime(): void;
    centerCurDom: HTMLElement;
    centerDurDom: HTMLElement;
    extraEls: HTMLElement[];
    changeLiveState(isLive: any): void;
    updateTime(time: any): void;
    minWidthTime(timeStr: any): any;
    resetActive(): void;
    render(): string;
}
import Plugin from "../../plugin";
