/**
* Check whether there is a player instance in the current dom
* @param {Element} root
*/
export function checkPlayerRoot(root: Element): any;
declare const InstManager_base: import("eventemitter3").EventEmitterStatic;
/**
 * Design as a class so that can be inherited to enhance functionality
 */
export class InstManager extends InstManager_base {
    static getInstance(): any;
    constructor();
    add(player: any): void;
    remove(player: any): void;
    /**
     * @private
     * @param {IterateFunction} fn
     * @param {?boolean} endEarly
     */
    private _iterate;
    /**
     * @param {IterateFunction} fn
     */
    forEach(fn: IterateFunction): void;
    /**
     * Iterates over items of store, returning the first one
     * @param {IterateFunction} fn
     * @returns {Player}
     */
    find(fn: IterateFunction): Player;
    /**
     * @param {IterateFunction} fn
     * @returns {Player[]}
     */
    findAll(fn: IterateFunction): Player[];
    /**
     * 设置实例的用户行为激活状态
     * @param { number | string } playerId
     * @param { boolean } isActive
     * @returns { number | null }
     */
    setActive(playerId: number | string, isActive?: boolean): number | null;
    /**
     * 获取当前处理激活态的实例id
     * @returns { number | null }
     */
    getActiveId(): number | null;
    /**
     * 标记下一个实例
     * @param { number | string } playerId
     * @param { boolean } isNext
     * @returns { number | null }
     */
    setNext(playerId: number | string, isNext?: boolean): number | null;
}
export type IterateFunction = (Player: any) => boolean | null;
export type Player = import('./player').default;
export {};
