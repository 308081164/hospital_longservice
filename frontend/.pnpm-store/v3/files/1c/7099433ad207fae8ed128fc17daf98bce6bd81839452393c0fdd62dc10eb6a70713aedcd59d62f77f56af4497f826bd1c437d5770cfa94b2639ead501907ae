export default PIP;
declare class PIP extends IconPlugin {
    static get defaultConfig(): {
        position: string;
        index: number;
        showIcon: boolean;
        preferDocument: boolean;
        width: any;
        height: any;
        docPiPNode: any;
        docPiPStyle: any;
    };
    static checkWebkitSetPresentationMode(video: any): boolean;
    beforeCreate(args: any): void;
    pMode: any;
    registerIcons(): {
        pipIcon: {
            icon: any;
            class: string;
        };
        pipIconExit: {
            icon: any;
            class: string;
        };
    };
    initIcons(): void;
    initPipEvents(): void;
    leavePIPCallback: () => void;
    pipWindow: any;
    enterPIPCallback: (e: any) => void;
    onWebkitpresentationmodechanged: (e: any) => void;
    switchPIP: (e: any) => boolean;
    copyStyleIntoPiPWindow(pipWin: any): void;
    requestPIP(): boolean;
    /**
     * 退出画中画
     */
    exitPIP(): boolean;
    /**
     * 处于画中画状态
     */
    get isPip(): boolean;
    isPIPAvailable(): boolean;
    isDocPIPAvailable(): boolean;
    render(): string;
}
import IconPlugin from "../common/iconPlugin";
