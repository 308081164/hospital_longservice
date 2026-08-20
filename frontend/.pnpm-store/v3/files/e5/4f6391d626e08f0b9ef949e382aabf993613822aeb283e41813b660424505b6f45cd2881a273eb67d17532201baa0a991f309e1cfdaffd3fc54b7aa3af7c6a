export default class OptionsIcon extends Plugin {
    /**
     * @type { IOptionsIconConfig }
     */
    static get defaultConfig(): IOptionsIconConfig;
    constructor(args: any);
    isIcons: boolean;
    isActive: boolean;
    curValue: any;
    curIndex: number;
    activeEvent: string;
    initIcons(): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    getTextByLang(item: any, key: any, lang: any): any;
    onEnter: (e: any) => void;
    switchActiveState: (e: any) => void;
    onLeave: (e: any) => void;
    onListEnter: (e: any) => void;
    enterType: number;
    onListLeave: (e: any) => void;
    toggle(isActive: any): void;
    onItemClick(e: any, data: any): void;
    curItem: any;
    onIconClick(e: any): void;
    changeCurrentText(): void;
    renderItemList(itemList: any, curIndex: any): void;
    optionsList: OptionList;
    _resizeList(): void;
    render(): string;
}
export type IOptionsIconConfig = {
    [propName: string]: any;
    isShow?: boolean;
    index?: number;
    list?: Array<any>;
    listType?: string;
    hidePortrait?: boolean;
};
import Plugin from "../../plugin";
import OptionList from "./optionList";
