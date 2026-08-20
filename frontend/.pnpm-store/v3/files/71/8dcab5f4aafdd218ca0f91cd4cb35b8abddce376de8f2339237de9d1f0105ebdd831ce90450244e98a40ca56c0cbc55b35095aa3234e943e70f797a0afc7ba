export default Controls;
export type IControlsConfig = {
    [propName: string]: any;
    disable?: boolean;
    autoHide?: boolean;
    mode?: "flex" | "normal" | "bottom";
    initShow?: boolean;
};
/**
  * @typedef {{
  *   disable?: boolean,
  *   autoHide?: boolean,
  *   mode?: "flex"|"normal"|"bottom",
  *   initShow?: boolean,
  *   [propName: string]: any
  * }} IControlsConfig
  */
declare class Controls extends Plugin {
    /**
     * @type IControlsConfig
     */
    static get defaultConfig(): IControlsConfig;
    beforeCreate(args: any): void;
    /**
     * @type { HTMLElement}
     * @readonly
     */
    readonly left: HTMLElement;
    /**
     * @type { HTMLElement}
     * @readonly
     */
    readonly center: HTMLElement;
    /**
     * @type { HTMLElement}
     * @readonly
     */
    readonly right: HTMLElement;
    /**
     * @type { HTMLElement}
     * @readonly
     */
    readonly innerRoot: HTMLElement;
    onMouseEnter: (e: any) => void;
    onMouseLeave: (e: any) => void;
    focus(): void;
    focusAwhile(): void;
    blur(): void;
    recoverAutoHide(): void;
    pauseAutoHide(): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    /**
     * @type {string}
     */
    get mode(): string;
    /**
     *
     * @param {} plugin
     * @param { {config?: {[propName: string]: any}, position?:string, root?: HTMLElement, pluginName?: string}} options
     * @param { string } name
     * @returns { any }
     */
    registerPlugin(plugin: any, options: {
        config?: {
            [propName: string]: any;
        };
        position?: string;
        root?: HTMLElement;
        pluginName?: string;
    }, name: string): any;
    render(): string;
}
import Plugin from "../../plugin";
