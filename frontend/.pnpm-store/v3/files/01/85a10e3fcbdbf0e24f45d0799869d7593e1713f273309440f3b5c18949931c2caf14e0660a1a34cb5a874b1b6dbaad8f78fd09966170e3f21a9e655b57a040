export type Player = import('../player').default;
export type IPlayerOptions = import('../defaultConfig').IPlayerOptions;
export type IBasePluginOptions = {
    [propName: string]: any;
    index?: number;
    player: Player;
    pluginName: string;
    config: {
        [propName: string]: any;
    };
};
/**
 * @typedef { import ('../player').default } Player
 */
/**
 * @typedef { import ('../defaultConfig').IPlayerOptions } IPlayerOptions
 */
/**
  * @typedef {{
  * index?: number,
  * player: Player,
  * pluginName: string,
  * config: {
  *   [propName: string]: any
  * },
  * [propName: string]: any;
  * }} IBasePluginOptions
 */
declare class BasePlugin {
    static defineGetterOrSetter(Obj: any, map: any): void;
    static defineMethod(Obj: any, map: any): void;
    /**
     * @type { { [propName: string]: any } }
     */
    static get defaultConfig(): {
        [propName: string]: any;
    };
    /**
     * @type { string }
     */
    static get pluginName(): string;
    /**
     * @param { IBasePluginOptions } args
     */
    constructor(args: IBasePluginOptions);
    /**
     * @private
     */
    private __args;
    /**
     * @private
     */
    private __events;
    __onceEvents: {};
    config: {
        [propName: string]: any;
    };
    /**
     * @readonly
     * @type { Player }
     */
    readonly player: Player;
    /**
       * @readonly
       * @type { IPlayerOptions }
       */
    readonly playerConfig: IPlayerOptions;
    /**
       * @readonly
       * @type {string}
       */
    readonly pluginName: string;
    /**
     * @param { IBasePluginOptions } args
     */
    beforeCreate(args: IBasePluginOptions): void;
    afterCreate(): void;
    beforePlayerInit(): void;
    onPluginsReady(): void;
    afterPlayerInit(): void;
    destroy(): void;
    /**
     * @protected
     * @param { any } args
     */
    protected __init(args: any): void;
    logger: any;
    /**
     * 更新语言
     * @param { string } lang
     */
    updateLang(lang: string): void;
    /**
     * @type { string }
     */
    get lang(): string;
    get i18n(): import("../lang/i18n").IXGI18nText;
    get i18nKeys(): {
        [propName: string]: string;
    };
    /**
     * @description当前支持的事件类型
     * @type { 'touch' | 'mouse' }
     */
    get domEventType(): "touch" | "mouse";
    /**
     *
     * @param { string | Array<string> } event
     * @param { Function } callback
     * @returns
     */
    on(event: string | Array<string>, callback: Function): void;
    /**
     *
     * @param { string } event
     * @param { Function } callback
     * @returns
     */
    once(event: string, callback: Function): void;
    /**
     *
     * @param { string } event
     * @param { Function } callback
     * @returns
     */
    off(event: string, callback: Function): void;
    offAll(): void;
    /**
     *
     * @param { string } event
     * @param  {...any} res
     * @returns
     */
    emit(event: string, ...res: any[]): void;
    emitUserAction(event: any, action: any, params?: {}): void;
    /**
     * @param { string } hookName
     * @param { Function } handler
     * @param { {pre: Function| null , next: Function | null} } preset
     * @returns
     */
    hook(hookName: string, handler: Function, preset?: {
        pre: Function | null;
        next: Function | null;
    }, ...args: any[]): any;
    /**
     * @param { string } hookName
     * @param { (plugin: any, ...args) => boolean | Promise<any> } handler
     * @param  {...any} args
     * @returns { boolean } isSuccess
     */
    useHooks(hookName: string, handler: (plugin: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): boolean;
    /**
     * @param { string } hookName
     * @param { (plugin: any, ...args) => boolean | Promise<any> } handler
     * @param  {...any} args
     * @returns { boolean } isSuccess
     */
    removeHooks(hookName: string, handler: (plugin: any, ...args: any[]) => boolean | Promise<any>, ...args: any[]): boolean;
    /**
     * 注册子插件
     * @param { any } plugin
     * @param { any } [options]
     * @param { string } [name]
     * @returns { any }
     */
    registerPlugin(plugin: any, options?: any, name?: string): any;
    /**
     *
     * @param { string } name
     * @returns { any | null }
     */
    getPlugin(name: string): any | null;
    __destroy(): void;
}
import Util from "../utils/util";
import Sniffer from "../utils/sniffer";
import Errors from "../error";
import * as Events from "../events";
import XG_DEBUG from "../utils/debug";
export { BasePlugin as default, Util, Sniffer, Errors, Events, XG_DEBUG };
