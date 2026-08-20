declare namespace util {
    /**
     *
     * @param { string } el
     * @param { string } [tpl=]
     * @param { {[propName: string]: any }} [attrs={}]
     * @param { string } [cname='']
     * @returns { HTMLElement | null }
     */
    function createDom(el?: string, tpl?: string, attrs?: {
        [propName: string]: any;
    }, cname?: string): HTMLElement;
    /**
     *
     * @param { string } html
     * @param { {[propName: string]: any} } [attrs={}]
     * @param { string } [classname=""]
     * @returns { HTMLElement | null }
     */
    function createDomFromHtml(html: string, attrs?: {
        [propName: string]: any;
    }, classname?: string): HTMLElement;
    /**
     *
     * @param { HTMLElement } el
     * @param { string } className
     * @returns { boolean }
     */
    function hasClass(el: HTMLElement, className: string): boolean;
    /**
     *
     * @param { HTMLElement } el
     * @param { string } className
     * @returns { void }
     */
    function addClass(el: HTMLElement, className: string): void;
    /**
     *
     * @param { HTMLElement } el
     * @param { string } className
     * @returns { void }
     */
    function removeClass(el: HTMLElement, className: string): void;
    /**
     *
     * @param { HTMLElement } el
     * @param { string } className
     * @returns { void }
     */
    function toggleClass(el: HTMLElement, className: string): void;
    /**
     *
     * @param { string | Object } args0
     * @param { string } [className]
     * @returns { string }
     */
    function classNames(...args: any[]): string;
    /**
     *
     * @param { HTMLElement } el
     * @param { string } sel
     * @returns { HTMLElement }
     */
    function findDom(el: HTMLElement, sel: string): HTMLElement;
    /**
     *
     * @param { HTMLElement } dom
     * @param { string } key
     * @returns { any }
     */
    function getCss(dom: HTMLElement, key: string): any;
    function padStart(str: any, length: any, pad: any): string;
    /**
     *
     * @param { number } time
     * @returns { string }
     */
    function format(time: number): string;
    /**
     *
     * @param { Object } e
     * @returns { Object }
     */
    function event(e: any): any;
    /**
     *
     * @param { any } obj
     * @returns { string }
     */
    function typeOf(obj: any): string;
    /**
     *
     * @param { any } dst
     * @param { any } src
     * @returns { any }
     */
    function deepCopy(dst: any, src: any): any;
    /**
     *
     * @param { any } dst
     * @param { any } src
     * @returns { any }
     */
    function deepMerge(dst: any, src: any): any;
    function getBgImage(el: any): string;
    /**
     *
     * @param {  HTMLElement } dom
     * @returns { HTMLElement | null }
     */
    function copyDom(dom: HTMLElement): HTMLElement;
    /**
     *
     * @param { any } context
     * @param { string } eventName
     * @param { function } intervalFunc
     * @param { number } frequency
     */
    function setInterval(context: any, eventName: string, intervalFunc: Function, frequency: number): void;
    /**
     *
     * @param { any } context
     * @param { string } eventName
     * @returns { void }
     */
    function clearInterval(context: any, eventName: string): void;
    /**
     *
     * @param { any } context
     * @param { function } fun
     * @param { number } time
     * @returns { number }
     */
    function setTimeout(context: any, fun: Function, time: number): number;
    /**
     *
     * @param { any } context
     * @param { number } id
     */
    function clearTimeout(context: any, id: number): void;
    /**
     *
     * @param { any } context
     */
    function clearAllTimers(context: any): void;
    /**
     *
     * @param { string } name
     * @param { string } imgUrl
     * @param { number } [width]
     * @param { number } [height]
     * @returns { HTMLElement }
     */
    function createImgBtn(name: string, imgUrl: string, width?: number, height?: number): HTMLElement;
    /**
     *
     * @param { string } hex
     * @param { string | number } alpha
     * @returns { string }
     */
    function Hex2RGBA(hex: string, alpha: string | number): string;
    /**
     *
     * @returns { HTMLElement | null }
     */
    function getFullScreenEl(): HTMLElement;
    /**
     * @param { any }
     * @returns { boolean }
     */
    function checkIsFunction(fun: any): boolean;
    /**
     * @param { any }
     * @returns { boolean }
     */
    function checkIsObject(obj: any): boolean;
    /**
     * @param { HTMLElement }
     */
    function hide(dom: any): void;
    /**
     * @param { HTMLElement }
     * @param { block | flex | inline-block | inline-flex } [display]
     */
    function show(dom: any, display: any): void;
    /**
     *
     * @param { any } val
     * @returns { boolean }
     */
    function isUndefined(val: any): boolean;
    function isNotNull(val: any): boolean;
    /**
     *
     * @param { HTMLElement } dom
     * @param { string } [text]
     * @returns
     */
    function setStyleFromCsstext(dom: HTMLElement, text?: string): void;
    /**
     *
     * @param { HTMLElement } dom
     * @param { Array<string> } [list] attribute names to filter
     * @returns { {} | {[propName: string]: any;} }
     */
    function filterStyleFromText(dom: HTMLElement, list?: string[]): {} | {
        [propName: string]: any;
    };
    /**
     *
     * @param { HTMLElement } dom
     * @returns { {} | {[propName: string]: any;} }
     */
    function getStyleFromCsstext(dom: HTMLElement): {} | {
        [propName: string]: any;
    };
    function preloadImg(url: any, onload?: () => void, onerror?: () => void): void;
    function stopPropagation(e: any): void;
    function scrollTop(): number;
    function scrollLeft(): number;
    function checkTouchSupport(): boolean;
    function getBuffered2(vbuffered: any, maxHoleDuration?: number): XgplayerTimeRange;
    /**
     * @description css中有zoom的时候，位移会等比缩放，但是元素的宽高不会等比缩放，所以通过该api做统一
     * https://bugs.chromium.org/p/chromium/issues/detail?id=429140#c8
     * @param {Event} e
     * @param {number} zoom
     * @returns
     */
    function getEventPos(e: Event, zoom?: number): {
        x: number;
        y: number;
        clientX: number;
        clientY: number;
        offsetX: number;
        offsetY: number;
        pageX: number;
        pageY: number;
    };
    function requestAnimationFrame(callback: any): any;
    function getHostFromUrl(url: any): string;
    function cancelAnimationFrame(frameId: any): void;
    /**
     * @desc Check whether it is MediaSource start
     * @param { HTMLVideoElement | HTMLAudioElement | HTMLElement } video
     * @returns { boolean }
     */
    function isMSE(video: HTMLElement | HTMLVideoElement | HTMLAudioElement): boolean;
    function isBlob(url: any): boolean;
    /**
     * @param { number } did
     * @returns { string }
     */
    function generateSessionId(did?: number): string;
    function createEvent(eventName: any): Event;
    /**
     * @description Adjust currentTime according to duration, when ended is true and currentTime is less than duration, or
     *              currentTime is greater than duration, return duration
     * @param { number } time
     * @param { number } duration
     * @param { boolean } isEnded
     * @returns { number } Adjusted time
     */
    function adjustTimeByDuration(time: number, duration: number, isEnded: boolean): number;
    function createPositionBar(className: any, root: any): HTMLElement;
    function getTransformStyle(pos?: {
        x: number;
        y: number;
        scale: number;
        rotate: number;
    }, transformValue?: string): string;
    /**
     * @description 角度换算
     * @param {number} val
     * @returns {number}
     */
    function convertDeg(val: number): number;
    function getIndexByTime(time: any, segments: any): number;
    function getOffsetCurrentTime(currentTime: any, segments: any, index?: number): any;
    /**
     *
     * @param {*} offsetTime
     * @param {*} segments
     * @returns
     */
    function getCurrentTimeByOffset(offsetTime: any, segments: any): any;
}
export function checkIsCurrentVideo(element: any, playerId: any, key: any): boolean;
export function debounce(func: any, wait: any, options: any): {
    (...args: any[]): any;
    cancel: () => void;
    flush: () => any;
    pending: () => boolean;
};
export function throttle(func: any, wait: any, options: any): {
    (...args: any[]): any;
    cancel: () => void;
    flush: () => any;
    pending: () => boolean;
};
/**
 * @returns { string }
 */
export function getLang(): string;
import XgplayerTimeRange from "./xgplayerTimeRange";
export { util as default };
