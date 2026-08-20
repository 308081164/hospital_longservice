import { RecyclableNodeFactory } from '../utils';
export type Configure = {
    container?: HTMLElement;
    containerStyle?: Partial<CSSStyleDeclaration>;
    player?: any;
    overlap?: boolean;
    area?: {
        start?: number;
        end?: number;
        lines?: number;
    };
    hooks?: GlobalHooks;
    live?: boolean;
    comments?: Array<CommentData>;
    direction?: 'r2l' | 'l2r' | 'b2t' | 't2b';
    needResizeObserver?: boolean;
    dropStaleComments?: boolean;
    channelSize?: number;
    maxCommentsLength?: number;
    bulletOffset?: number;
    interval?: number;
    highScorePriority?: boolean;
    chaseEffect?: boolean;
    disableCopyDOM?: boolean;
    defaultOff?: boolean;
    bOffset?: number;
    mouseControl?: boolean;
    mouseEnterControl?: boolean;
    mouseControlPause?: boolean;
};
export type CommentDataMode = 'scroll' | 'top' | 'bottom';
export type CommentData = {
    id: string;
    mode: CommentDataMode;
    start: number;
    duration: number;
    prior: boolean;
    noDiscard?: boolean;
    score?: number;
    txt?: string;
    el?: HTMLElement | Function;
    elLazyInit?: boolean;
    attached_?: boolean;
    realTime?: boolean;
    loop?: boolean;
    color?: string;
    bookChannelId?: [number, any];
    disableCopyDOM?: boolean;
    moveV?: number;
    style?: Partial<CSSStyleDeclaration>;
    like?: {
        el: HTMLElement;
        style: Partial<CSSStyleDeclaration>;
    };
    eventListeners?: Array<{
        event: 'string';
        listener: () => {};
        useCapture: boolean;
    }>;
};
export type GlobalHooks = {
    bulletCreateEl?: (item: CommentData, recycler?: RecyclableNodeFactory) => Promise<HTMLElement | {
        el: HTMLElement;
    }>;
    bulletAttaching?: (item: CommentData) => void;
    bulletAttached?: (item: CommentData, el: HTMLElement) => void;
    bulletDetaching?: (item: CommentData) => void;
    bulletDetached?: (item: CommentData, el: HTMLElement) => void;
    nodeRecyclerGC?: (el: HTMLElement) => void;
};
