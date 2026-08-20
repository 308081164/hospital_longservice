export default class ScreenShot extends IconPlugin {
    static get defaultConfig(): {
        position: string;
        index: number;
        quality: number;
        type: string;
        format: string;
        width: number;
        height: number;
        saveImg: boolean;
        fitVideo: boolean;
        disable: boolean;
        name: string;
    };
    beforeCreate(args: any): void;
    initSize: (data: any) => void;
    onClickBtn(e: any): void;
    saveScreenShot(data: any, filename: any): void;
    createCanvas(width: any, height: any): void;
    canvasCtx: CanvasRenderingContext2D;
    canvas: HTMLCanvasElement;
    shot(width: any, height: any, option?: {
        quality: number;
        type: string;
    }): Promise<any>;
    registerIcons(): {
        screenshotIcon: any;
    };
    render(): string;
}
import IconPlugin from "../common/iconPlugin";
