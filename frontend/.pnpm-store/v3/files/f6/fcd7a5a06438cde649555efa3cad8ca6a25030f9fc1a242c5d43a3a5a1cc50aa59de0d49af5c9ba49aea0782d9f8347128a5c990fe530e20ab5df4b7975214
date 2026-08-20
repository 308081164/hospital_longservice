export default DanmuIcon;
declare class DanmuIcon extends Plugin {
    static get defaultConfig(): {
        position: string;
        index: number;
        onSwitch: (event: any, state: any) => void;
    };
    onStateChange(e: any): void;
    registerIcons(): {
        openDanmu: {
            icon: string;
            class: string;
        };
        closeDanmu: {
            icon: string;
            class: string;
        };
    };
    switchState(isOpen: any): void;
    initIcons(): void;
    switchTips(isOpen: any): void;
    render(): string;
}
import Plugin from "../../plugin";
