export default Start;
export type IStartConfig = {
    [propName: string]: any;
    isShowPause?: boolean;
    isShowEnd?: boolean;
    disableAnimate?: boolean;
    mode?: 'hide' | 'show' | 'auto';
};
declare class Start extends Plugin {
    /**
     * @type IStartConfig
     */
    static get defaultConfig(): IStartConfig;
    constructor(args: any);
    autoPlayStart: boolean;
    /**
     * @public
     * This method can be overridden.
     */
    public listenEvents(): void;
    /**
     * @public
     */
    public bindClickEvents(): void;
    clickHandler: any;
    onPlayerReset: () => void;
    onAutoplayStart: () => void;
    registerIcons(): {
        startPlay: {
            icon: any;
            class: string;
        };
        startPause: {
            icon: any;
            class: string;
        };
    };
    initIcons(): void;
    /**
     * @param {string} [value]
     * @returns
     */
    show(value?: string): void;
    focusHide(): void;
    recover(): void;
    switchStatus(isAnimate: any): void;
    animate(endShow: any): void;
    _animateId: any;
    endAnimate(): void;
    /**
     * @public
     */
    public switchPausePlay(e: any): void;
    /**
     * @deprecated
     */
    onPlayPause(status: any): void;
    /**
     * @param {'play'|'pause'} status
     */
    toggleTo(status: 'play' | 'pause'): void;
    render(): string;
}
import Plugin from "../../plugin";
