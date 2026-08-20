/**
 * @typedef {import('../../defaultConfig').IDefinition} IDefinition
 */
/**
 * @typedef {{
 *   position?: string,
 *   index?: number,
 *   list?: Array<IDefinition>,
 *   defaultDefinition?: any,
 *   disable?: any,
 *   hidePortrait?: boolean,
 *   className?: string
 * }} IDefinitionConfig
 */
export default class DefinitionIcon extends OptionsIcon {
    /**
     * @type IDefinitionConfig
     */
    static get defaultConfig(): IDefinitionConfig;
    beforeCreate(args: any): void;
    curTime: number;
    isPaused: boolean;
    initDefinition(): void;
    changeDefinitionList(list: any): void;
    changeDefinition(to: any, from: any): void;
    onItemClick(e: any, data: any, ...args: any[]): void;
}
export type IDefinition = import('../../defaultConfig').IDefinition;
export type IDefinitionConfig = {
    position?: string;
    index?: number;
    list?: Array<IDefinition>;
    defaultDefinition?: any;
    disable?: any;
    hidePortrait?: boolean;
    className?: string;
};
import OptionsIcon from "../common/optionsIcon";
