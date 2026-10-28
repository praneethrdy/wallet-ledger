/** What each reconciliation check means, in words a support engineer can act on. */
export const CHECKS: Record<string, string> = {
  GLOBAL_BALANCE: "Total debits and credits differ for a currency",
  TRANSACTION_UNBALANCED: "A transaction’s legs don’t add up",
  TRANSACTION_MISSING_SIDE: "A transaction has only debits or only credits",
  NEGATIVE_WALLET: "A wallet’s balance is below zero",
  DEPOSIT_WITHOUT_MATCHING_TRANSACTION: "A succeeded deposit has no ledger transaction",
  TRANSACTION_WITHOUT_DEPOSIT: "A deposit transaction has no deposit behind it",
  REVERSED_TWICE: "A transaction was reversed more than once",
  REVERSAL_OF_REVERSAL: "A reversal was itself reversed",
  STALE_PENDING_DEPOSIT: "A deposit has been pending for too long",
  RUN_ERROR: "The run itself failed before finishing",
};

export function checkLabel(check: string): string {
  return CHECKS[check] ?? check;
}
