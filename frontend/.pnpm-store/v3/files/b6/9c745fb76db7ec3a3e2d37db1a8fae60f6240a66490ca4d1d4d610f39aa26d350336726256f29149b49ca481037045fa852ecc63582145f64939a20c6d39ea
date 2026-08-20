/**
 * @typedef {{
 *  position?: string,
 *  index?: number,
 *  list?: Array<number | { [propName: string]: any }>,
 *  className?: string,
 *  hidePortrait?: boolean,
 *  [propName: string]: any
 * }} IPlaybackRateConfig
 */
export default class PlaybackRate extends OptionsIcon {
    /**
     * @type IPlaybackRateConfig
     */
    static get defaultConfig(): IPlaybackRateConfig;
    curRate: number;
    beforeCreate(args: any): void;
    onItemClick(e: any, _data: any): boolean;
    renderItemList(): void;
}
export type IPlaybackRateConfig = {
    [propName: string]: any;
    position?: string;
    index?: number;
    list?: (number | {
        [propName: string]: any;
    })[];
    className?: string;
    hidePortrait?: boolean;
};
import OptionsIcon from "../common/optionsIcon";
