/**
 * UIEngine Context
 *
 * Provides engine state and functions to all child components
 */

import { createContext, useContext } from 'react';
import type { UIEngineContextValue, ExpressionContext, ActionDefinition } from './types';

// Default no-op context value
const defaultContext: UIEngineContextValue = {
  state: {},
  setState: () => {
    console.warn('UIEngine context not initialized');
  },
  data: {},
  params: {},
  isLoading: {},
  errors: {},
  isPageLoading: false,
  dispatch: async () => {
    console.warn('UIEngine context not initialized');
  },
  refetch: () => {
    console.warn('UIEngine context not initialized');
  },
  openModals: {},
  openModal: () => {
    console.warn('UIEngine context not initialized');
  },
  closeModal: () => {
    console.warn('UIEngine context not initialized');
  },
  evaluateExpression: (expr) => expr,
};

/**
 * UIEngine React context
 */
export const UIEngineContext = createContext<UIEngineContextValue>(defaultContext);

/**
 * Item context - carries the current `each` loop bindings (e.g. `item`/`index`
 * or a custom `as` name like `candidate`) down through the component tree.
 * resolveValue() defers action objects (onClick/onChange) unevaluated until
 * dispatch time, by which point the loop that produced them has already
 * returned -- so any component that dispatches an action must merge this
 * back in, or expressions like {{candidate.id}} inside that action resolve
 * to undefined. See ComponentRenderer's `each` handling for the provider.
 */
const ItemContext = createContext<Partial<ExpressionContext> | undefined>(undefined);

export const ItemContextProvider = ItemContext.Provider;

/**
 * Hook to access the current `each` loop's item bindings, if any.
 * Components that call dispatch() must merge this in as additionalContext.
 */
export function useItemContext(): Partial<ExpressionContext> | undefined {
  return useContext(ItemContext);
}

/**
 * Hook to access UIEngine context
 */
export function useUIEngine(): UIEngineContextValue {
  const context = useContext(UIEngineContext);
  if (!context) {
    throw new Error('useUIEngine must be used within a UIEngineProvider');
  }
  return context;
}

/**
 * Hook to access just the dispatch function
 */
export function useDispatch() {
  const { dispatch } = useUIEngine();
  return dispatch;
}

/**
 * Hook to access page state
 */
export function usePageState<T = unknown>(key?: string): T {
  const { state } = useUIEngine();
  if (key) {
    return state[key] as T;
  }
  return state as T;
}

/**
 * Hook to access query data
 */
export function usePageData<T = unknown>(sourceName?: string): T {
  const { data } = useUIEngine();
  if (sourceName) {
    return data[sourceName] as T;
  }
  return data as T;
}

/**
 * Hook to evaluate an expression with current context
 */
export function useExpression<T = unknown>(expression: unknown): T {
  const { evaluateExpression } = useUIEngine();
  return evaluateExpression(expression) as T;
}
