/**
 * @typedef { {
 *   position: string,
 *   index: number,
 *   disable: boolean,
 *   [propName: string]: any
 *  } } IDownloadConfig
 */
export default class Download extends IconPlugin {
    /**
     * @type IDownloadConfig
     */
    static get defaultConfig(): IDownloadConfig;
    constructor(args: any);
    timer: any;
    isLock: boolean;
    _handler: any;
    registerIcons(): {
        download: any;
    };
    download: (e: any) => void;
    getAbsoluteURL(url: any): any;
    render(): string;
}
export type IDownloadConfig = {
    [propName: string]: any;
    position: string;
    index: number;
    disable: boolean;
};
import IconPlugin from "../common/iconPlugin";
