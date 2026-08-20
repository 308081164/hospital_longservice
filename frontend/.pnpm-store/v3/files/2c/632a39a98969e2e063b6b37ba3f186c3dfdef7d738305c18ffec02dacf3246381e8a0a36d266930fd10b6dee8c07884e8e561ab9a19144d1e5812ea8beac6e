export default class InnerList {
    constructor(args: any);
    fragments: any;
    _callBack: any;
    fragConfig: {
        fragFocusClass: any;
        fragAutoFocus: boolean;
        fragClass: any;
    };
    style: any;
    duration: number;
    cachedIndex: number;
    playedIndex: number;
    focusIndex: number;
    updateDuration(duration: any): void;
    updateProgress(type?: string, data?: {
        newIndex: number;
        curIndex: number;
        millisecond: number;
    }): void;
    updateFocus(data: any): void;
    update(data: {
        cached: number;
        played: number;
    }, duration: any): void;
    findIndex(time: any, curIndex: any): any;
    findHightLight(): {
        dom: Element;
        pos: DOMRect;
    };
    findFragment(index: any): {
        dom: Element;
        pos: DOMRect;
    };
    unHightLight(): void;
    setHightLight(index: any): {
        dom: Element;
        pos: DOMRect;
    };
    destroy(): void;
    progressList: any;
    /**
     * 重置当前
     * @param {*} newOptions
     */
    reset(newOptions: any): void;
    render(): HTMLElement;
    root: HTMLElement;
}
