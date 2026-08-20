export default Keyboard;
export type IKeyboardConfig = {
    [propName: string]: any;
    seekStep?: number;
    checkVisible?: boolean;
    disableBodyTrigger?: boolean;
    keyCodeMap?: {
        [propName: string]: any;
    };
    disable: boolean;
    playbackRate: number;
    isIgnoreUserActive: boolean;
};
declare class Keyboard extends BasePlugin {
    /**
     * @type IKeyboardConfig
     */
    static get defaultConfig(): IKeyboardConfig;
    mergekeyCodeMap(): void;
    seekStep: number;
    keyCodeMap: {
        space: {
            keyCode: number;
            action: string;
            disable: boolean;
            disablePress: boolean;
            noBodyTarget: boolean;
        };
        up: {
            keyCode: number;
            action: string;
            disable: boolean;
            disablePress: boolean;
            noBodyTarget: boolean;
        };
        down: {
            keyCode: number;
            action: string;
            disable: boolean;
            disablePress: boolean;
            noBodyTarget: boolean;
        };
        left: {
            keyCode: number;
            action: string;
            disablePress: boolean;
            disable: boolean;
        };
        right: {
            keyCode: number;
            action: string;
            pressAction: string;
            disablePress: boolean;
            disable: boolean;
        };
        esc: {
            keyCode: number;
            action: string;
            disablePress: boolean;
            disable: boolean;
        };
    };
    _keyState: {
        isKeyDown: boolean;
        isBodyKeyDown: boolean;
        isPress: boolean;
        tt: number;
        playbackRate: number;
    };
    setConfig(newConfig: any): void;
    checkIsVisible(): boolean;
    checkCode(code: any, isBodyTarget: any): boolean;
    downVolume(event: any): void;
    upVolume(event: any): void;
    seek(event: any): void;
    seekBack(event: any): void;
    changePlaybackRate(event: any): void;
    playPause(event: any): void;
    exitFullscreen(event: any): void;
    onBodyKeyDown: (event: any) => void;
    onBodyKeyUp: (event: any) => void;
    onKeydown: (event: any) => void;
    onKeyup: (event: any) => void;
    handleKeyDown(e: any): void;
    handleKeyUp(e: any): void;
    handleKeyCode(curKeyCode: any, event: any, isPress: any): void;
    disable(): void;
    enable(): void;
}
import { BasePlugin } from "../../plugin";
