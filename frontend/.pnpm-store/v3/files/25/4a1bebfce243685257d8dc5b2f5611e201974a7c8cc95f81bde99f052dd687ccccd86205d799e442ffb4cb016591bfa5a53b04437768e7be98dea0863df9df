export type IMiniScreenConfig = {
    [propName: string]: any;
    index?: number;
    disable?: boolean;
    width?: number;
    height?: number;
    left?: number;
    top?: number;
    isShowIcon?: boolean;
    isScrollSwitch?: boolean;
    scrollTop?: number;
    disableDrag?: boolean;
};
/**
 * @typedef {{
 *   index?: number,
 *   disable?: boolean,
 *   width?: number,
 *   height?: number,
 *   left?: number, // 默认左下角
 *   top?: number, // 默认左下角
 *   isShowIcon?: boolean, // 是否显示icon
 *   isScrollSwitch?: boolean, // 滚动自动切换自动切换
 *   scrollTop?: number, // 触发滚动的高度
 *   disableDrag?: boolean, // 禁用拖拽
 *   [propName: string]: any
 * }} IMiniScreenConfig
 */
declare class MiniScreen extends Plugin {
    /**
     * @type IMiniScreenConfig
     */
    static get defaultConfig(): IMiniScreenConfig;
    constructor(args: any);
    isMini: boolean;
    isClose: boolean;
    pos: {
        left: any;
        top: any;
        height: any;
        width: any;
        scrollY: number;
    };
    lastStyle: {};
    beforeCreate(args: any): void;
    _draggabilly: Draggabilly;
    registerIcons(): {
        play: {
            icon: any;
            class: string;
        };
        pause: {
            icon: any;
            class: string;
        };
    };
    initIcons(): void;
    onCancelClick: (e: any) => void;
    onCenterClick: (e: any) => void;
    onScroll: (e: any) => void;
    getMini(): void;
    exitMini(): boolean;
    updatePos(pos: any): void;
    render(): string;
}
import MiniScreenIcon from "./miniScreenIcon";
import Plugin from "../../plugin";
import Draggabilly from "../../utils/draggabilly";
export { MiniScreen as default, MiniScreenIcon };
