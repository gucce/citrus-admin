import {Injectable} from "@angular/core";
import {Action, Store} from "@ngrx/store";
import {Test, TestGroup, TestDetail} from "../../model/tests";
import {AppState} from "../../state.module";
import {AsyncActionType, AsyncActions, Extender, IdMap, toArray, toIdMap} from "../../util/redux.util";
import {Effect} from "@ngrx/effects";
import {TestService} from "../../service/test.service";
import {Observable} from "rxjs";
import * as _ from "lodash";
import {log} from "util";
import {Log} from "../../util/decorator";

export type TestMap = IdMap<Test>;
export type TestGroupMap = IdMap<TestGroup>;
export type TestDetailMap = IdMap<TestDetail>;

export interface TestState {
    tests:IdMap<Test>,
    packages:IdMap<TestGroup>,
    testNames:string[],
    openTabs:string[],
    selectedTest:string,
    details: IdMap<TestDetail>
}

export const TestStateInit:TestState = {
    tests:{},
    packages: {},
    testNames: [],
    openTabs:[],
    selectedTest:'',
    details: {}
}

@Injectable()
export class TestStateEffects {
    constructor(
        private actions:AsyncActions,
        private testService:TestService,
    ) {}
    @Effect() package = this.actions
        .handleEffect(TestStateActions.PACKAGES, () => this.testService.getTestPackages())

    @Effect() detail = this.actions
        .handleEffect(TestStateActions.DETAIL, ({payload}) => this.testService.getTestDetail(payload))

}

@Injectable()
export class TestStateService {
    constructor(private store:Store<AppState>) {}

    get packages():Observable<TestGroup[]> { return this.store.select(s => s.tests.packages).map(toArray) }

    get openTabs():Observable<Test[]> { return this.store.select(p => p.tests.openTabs).map(toArray) }

    get testNames():Observable<string[]> { return this.tests.map(p => p.map(t => t.name)) }

    get tests():Observable<Test[]> { return this.packages.map(tg => tg.reduce((c:Test[], tg:TestGroup) => [...c, ...tg.tests], [] as Test[]))}

    get selectedTest():Observable<Test> {
        return (Observable.combineLatest(
            this.store.select(p => p.tests.selectedTest),
            this.store.select(p => p.tests.tests)
        ).map(([s,t]) => t[s]))
    }

    get selectedTestDetail():Observable<TestDetail> { return this.selectedTest.filter(t => t != null).switchMap(t => this.getDetail(t))}

    getDetail(test:Test):Observable<TestDetail> {
        return this.store.select(s => s.tests.details[test.name]).filter(d => d != null)
    }
}

@Injectable()
export class TestStateActions {
    static PACKAGES = AsyncActionType('TEST.PACKAGE')
    static DETAIL = AsyncActionType('TEST.DETAIL')
    static ADD_TAB = 'TEST.ADD_TAB';
    static REMOVE_TAB = 'TEST.REMOVE_TAB';
    static SELECT_TAB = 'TEST.SELECT_TAB';
    constructor(private store:Store<AppState>) {}
    fetchPackages() {
        this.store.dispatch({type:TestStateActions.PACKAGES.FETCH})
    }

    addTab(payload:Test) {
        this.store.dispatch({type:TestStateActions.ADD_TAB, payload})
    }

    removeTab(payload:Test) {
        this.store.dispatch({type:TestStateActions.REMOVE_TAB, payload})
    }

    selectTest(payload:Test) {
        this.store.dispatch({type:TestStateActions.SELECT_TAB, payload})
    }

    fetchDetails(payload: Test) {
        this.store.dispatch(({type:TestStateActions.DETAIL.FETCH, payload}))
    }
}

export function reduce(state:TestState = TestStateInit, action:Action) {
    const stateExtender = new Extender(state);
    switch (action.type) {
        case TestStateActions.PACKAGES.SUCCESS: {
            const packages = toIdMap(action.payload as TestGroup[], tg => tg.name) ;
            const tests = toIdMap((action.payload as TestGroup[]).reduce((c: Test[], tg: TestGroup) => [...c, ...tg.tests], [] as Test[]), t => t.name);
            const testNames = Object.keys(tests);
            return { ...state, packages, tests, testNames }
        }
        case TestStateActions.ADD_TAB: {
            const {name} = action.payload as Test;
            const exists = state.openTabs.indexOf(name);
            const openTabs = exists >= 0 ? state.openTabs : [...state.openTabs, name];
            return {...state, openTabs, selectedTest:name};
        }
        case TestStateActions.REMOVE_TAB: {
            const openTabs = state.openTabs.filter(t => t !== action.payload.name);
            let {selectedTest} = state;
            if (state.selectedTest === action.payload.name) {
                const i = state.openTabs.findIndex(t => t === state.selectedTest);
                selectedTest = openTabs[Math.max(Math.min(i -1, openTabs.length -1 ), 0)];
            }
            if (openTabs.length === 0) {
                selectedTest = '';
            }
            return {...state, selectedTest, openTabs};
        }
        case TestStateActions.SELECT_TAB: {
            if( state.selectedTest === action.payload.name ||
                state.openTabs.indexOf(action.payload.name) === -1) {
                return state;
            } else {
                return {...state, selectedTest: action.payload.name};
            }
        }
        case TestStateActions.DETAIL.SUCCESS: {
            const detail = action.payload as TestDetail;
            return {...state, details : {[detail.name]:detail}};
        }
    }
    return state;
}