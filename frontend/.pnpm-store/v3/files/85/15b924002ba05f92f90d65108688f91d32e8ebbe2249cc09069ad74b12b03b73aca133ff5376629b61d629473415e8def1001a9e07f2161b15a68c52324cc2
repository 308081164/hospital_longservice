/**
 * hook decorator, add hooks props for for an instance
 * @param { any } instance
 * @param { Array<string> } [hookNames]
 */
declare function hooksDescriptor(instance: any, presetHooks?: any[]): void;
/**
 * 给某个处理函数添加hook能力
 * @param { string } hookName
 * @param { Function } handler
 * @param { { pre?: any, next?:any } } preset
 * {
 *   pre: () => { // run beafore hook},
 *   next: () => { // run after hook return}
 * }
 */
export function hook(hookName: string, handler: Function, preset?: {
    pre?: any;
    next?: any;
}): any;
export class hook {
    /**
     * 给某个处理函数添加hook能力
     * @param { string } hookName
     * @param { Function } handler
     * @param { { pre?: any, next?:any } } preset
     * {
     *   pre: () => { // run beafore hook},
     *   next: () => { // run after hook return}
     * }
     */
    constructor(hookName: string, handler: Function, preset?: {
        pre?: any;
        next?: any;
    });
    __hooks: {};
}
/**
 * add hooks
 * @param { string } hookName
 * @param { Function } handler
 */
export function useHooks(hookName: string, handler: Function): boolean;
/**
 * Add hooks to a plugin
 * @param { string } pluginName
 * @param  {...any} args
 */
export function usePluginHooks(pluginName: string, ...args: any[]): any;
export function removePluginHooks(pluginName: any, ...args: any[]): any;
/**
 * remove hook
 * @param { string } hookName
 * @param { (plugin: any, ..args) => {} } handler
 * @returns void
 */
export function removeHooks(hookName: string, handler: (plugin: any, args: any) => {}): void;
export function delHooksDescriptor(instance: any): void;
export function runHooks(obj: any, hookName: any, handler: any, ...args: any[]): any;
export { hooksDescriptor as default };
