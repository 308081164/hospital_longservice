export type Player = import('../player').default;
export type IPluginOptions = {
    [propName: string]: any;
    index?: number;
    player: Player;
    pluginName: string;
    config: {
        [propName: string]: any;
    };
    root?: HTMLElement;
    position?: string;
};
/**
 * @typedef { import ('../player').default } Player
 */
/**
 * @typedef {{
 *  index?: number,
 *  player: Player,
 *  pluginName: string,
 *  config: {
 *   [propName: string]: any
 *  },
 *  root?: HTMLElement,
 *  position?: string,
 *  [propName: string]: any
 * }} IPluginOptions
*/
declare class Plugin extends BasePlugin {
    /**
      * 插入dom结构
      * @param { string | HTMLElement } html html字符串或者dom
      * @param { HTMLElement } parent
      * @param { number } index
      * @returns { HTMLElement }
      */
    static insert(html: string | HTMLElement, parent: HTMLElement, index?: number): HTMLElement;
    static get defaultConfig(): {};
    /**
     *
     * @param { HTMLElement } root
     * @param { string } querySelector
     * @param { string | Array<string> } eventType
     * @param { Function } callback
     * @param { boolean } [capture=false]
     * @returns
     */
    static delegate(root: HTMLElement, querySelector: string, eventType: string | Array<string>, callback: Function, capture?: boolean): any[];
    static get ROOT_TYPES(): {
        CONTROLS: string;
        ROOT: string;
    };
    static get POSITIONS(): {
        ROOT: string;
        ROOT_LEFT: string;
        ROOT_RIGHT: string;
        ROOT_TOP: string;
        CONTROLS_LEFT: string;
        CONTROLS_RIGTH: string;
        CONTROLS_RIGHT: string;
        CONTROLS_CENTER: string;
        CONTROLS: string;
    };
    /**
     * @param { IPluginOptions } args
     */
    constructor(args?: IPluginOptions);
    /**
     * @private
     */
    private __delegates;
    /**
     * @protected
     */
    protected __init(args: any): void;
    /**
     * @readonly
     */
    readonly icons: {};
    /**
     * @readonly
     * @type { HTMLElement }
     */
    readonly root: HTMLElement;
    /**
     * @readonly
     * @type { HTMLElement }
     */
    readonly parent: HTMLElement;
    /**
     * store the extra HTMLElements except root
     * @readonly
     * @type {HTMLElement[]}
     */
    readonly extraEls: HTMLElement[];
    /**
     * @readonly
     */
    readonly langText: {};
    /**
     * @private
     */
    private __registerChildren;
    /**
     * @private
     */
    private _children;
    updateLang(lang: any): void;
    changeLangTextKey(dom: any, key?: string): void;
    plugins(): any[];
    /**
     *
     */
    disable(): void;
    enable(): void;
    children(): {};
    registerPlugin(plugin: any, options?: {}, name?: string): any;
    registerIcons(): {};
    registerLanguageTexts(): {};
    /**
     *
     * @param { string } qs
     * @returns { HTMLElement | null }
     */
    find(qs: string): HTMLElement | null;
    /**
     * 绑定事件
     * @param { string | Array<string> } querySelector
     * @param { string | Array<string> | Function } eventType
     * @param { Function } [callback]
     */
    bind(querySelector: string | Array<string>, eventType: string | Array<string> | Function, callback?: Function, ...args: any[]): void;
    /**
     * 绑定事件
     * @param { string | Array<string> } querySelector
     * @param { string | Array<string> | Function } eventType
     */
    unbind(querySelector: string | Array<string>, eventType: string | Array<string> | Function, ...args: any[]): void;
    /**
     * 给插件设置样式
     * @param { string | {[propName: string]: any} } name
     * @param { ？ | any } value
     * @returns
     */
    setStyle(name: string | {
        [propName: string]: any;
    }, value: any): any;
    /**
     * 给插件根节点设置属性
     * @param { string | {[propName: string]: any} } name
     * @param { ？ | any } value
     * @returns
     */
    setAttr(name: string | {
        [propName: string]: any;
    }, value: any): void;
    /**
     *
     * @param { string } htmlStr
     * @param { Function } [callback]
     * @returns
     */
    setHtml(htmlStr: string, callback?: Function): void;
    /**
     *
     * @param { string } event
     * @param { Function } eventHandle
     * @param { boolean } [isBubble=false]
     * @returns
     */
    bindEL(event: string, eventHandle: Function, isBubble?: boolean): void;
    /**
     *
     * @param { string } event
     * @param { Function } eventHandle
     * @param { boolean } [isBubble]
     * @returns
     */
    unbindEL(event: string, eventHandle: Function, isBubble?: boolean): void;
    /**
     *
     * @param { string } [value]
     * @returns
     */
    show(value?: string): string;
    hide(): void;
    /**
     *
     * @param { string | HTMLElement } pdom
     * @param { HTMLElement} child
     * @returns { HTMLElement | null }
     */
    appendChild(pdom: string | HTMLElement, child: HTMLElement, ...args: any[]): HTMLElement | null;
    /**
     *
     * @returns { string | HTMLElement }
     */
    render(): string | HTMLElement;
}
export namespace ROOT_TYPES {
    const CONTROLS: string;
    const ROOT: string;
}
export namespace POSITIONS {
    const ROOT_1: string;
    export { ROOT_1 as ROOT };
    export const ROOT_LEFT: string;
    export const ROOT_RIGHT: string;
    export const ROOT_TOP: string;
    export const CONTROLS_LEFT: string;
    export const CONTROLS_RIGTH: string;
    export const CONTROLS_RIGHT: string;
    export const CONTROLS_CENTER: string;
    const CONTROLS_1: string;
    export { CONTROLS_1 as CONTROLS };
}
export namespace PLUGIN_STATE_CLASS {
    const ICON_DISABLE: string;
    const ICON_HIDE: string;
}
import BasePlugin from "./basePlugin";
export { Plugin as default };
