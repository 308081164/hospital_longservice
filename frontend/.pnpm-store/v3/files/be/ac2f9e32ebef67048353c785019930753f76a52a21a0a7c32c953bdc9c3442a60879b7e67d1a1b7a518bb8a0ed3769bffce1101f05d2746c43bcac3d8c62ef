export default Play;
declare class Play extends IconPlugin {
    static get defaultConfig(): {
        position: string;
        index: number;
        disable: boolean;
    };
    listenEvents(): void;
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
    btnClick: (e: any) => boolean;
    initIcons(): void;
    animate(paused: any): void;
    render(): string;
}
import IconPlugin from "../common/iconPlugin";
