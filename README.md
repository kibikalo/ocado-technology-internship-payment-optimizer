# Payment Optimization Algorithm: Solution Overview

The core of this solution utilizes a **backtracking algorithm** to explore various payment combinations and find the one that maximizes the total discount while adhering to all constraints.

## Development Process Overview

The solution was developed iteratively, breaking the problem into logical phases:

1.  **Foundation & Data Handling (Phase 1):**
    *   Implemented JSON data loading (using Jackson) and created core Java models (`Order`, `PaymentMethod`) with `BigDecimal` for currency.
    *   Implemented comprehensive data validation to ensure input integrity.
    *   Set up initial state management, including tracking remaining limits for payment methods.

2.  **Core Payment & Discount Logic (Phase 2):**
    *   Built foundational payment mechanics, allowing simulation of payments and limit adjustments.
    *   Developed detailed discount calculation logic, accurately implementing all specified rules (bank card, full/partial points, exclusivity).
    *   Created a function to determine the effective (post-discount) value of an order for any given payment strategy.

3.  **Optimization Algorithm (Phase 3):**
    *   Initially explored sorting heuristics and a greedy algorithm, which was iteratively refined.
    *   Recognizing the need for global optimality, a backtracking algorithm was then implemented. This approach systematically explores the solution space to find the payment combination that maximizes total discount while adhering to all constraints and preferences (like prioritizing points on a tie).

Throughout the process, emphasis was placed on separating concerns into distinct classes (e.g., for data loading, validation, payment processing, and optimization) to enhance code clarity, testability, and maintainability. Unit tests were utilized for verifying core calculation logic.

### Time Complexity

*   **Worst-Case:** In the worst-case scenario, a backtracking algorithm explores a significant portion of the decision tree. For each of the `N` orders, there are multiple ways to pay (using different payment methods, full payments, partial payments with points, etc.). If `M` is the number of payment methods and `P` represents the average number of distinct payment strategies per order (e.g., full payment with each of `M` methods, partial points with each of `M-1` traditional methods, full points), the complexity can be roughly estimated as exponential.
*   **Practical Performance:** The actual performance depends heavily on:
    *   The number of orders (`N`).
    *   The number of payment methods (`M`).
    *   The number of applicable promotions per order.
    *   The effectiveness of any pruning strategies (currently minimal in this implementation).
    *   The tightness of constraints (e.g., payment method limits). Tighter limits can prune branches faster.

### Scalability

*   **Small Datasets:** For small datasets (like the provided example with 4 orders and 3 payment methods), the backtracking approach is feasible and can find an optimal solution within a reasonable time (seconds on my pc).
*   **Large Datasets (e.g., 10000 orders):** The current backtracking implementation **will not scale** to handle thousands of orders. The exponential nature of the algorithm means the computation time will become prohibitively long.
*   **Untested on Large Datasets:** Due to time constraints during development, extensive testing on large datasets has not been performed. However, based on its theoretical complexity, performance degradation is expected.

## Algorithm's Solution Overview

Our backtracking algorithm processed the example orders and payment methods, yielding the following payment strategy:

---
*   **ORDER1 (Value: 100.00)**
    *   Paid with: 10.00 PUNKTY + 80.00 BosBankrut
    *   Discount: 10.00 (10% partial points discount)
---
*   **ORDER2 (Value: 200.00)**
    *   Paid with: 20.00 PUNKTY + 160.00 mZysk
    *   Discount: 20.00 (10% partial points discount)
---
*   **ORDER3 (Value: 150.00)**
    *   Paid with: 15.00 PUNKTY + 120.00 BosBankrut
    *   Discount: 15.00 (10% partial points discount)
---
*   **ORDER4 (Value: 50.00)**
    *   Paid with: 50.00 PUNKTY
    *   Discount: 7.50 (15% full points discount)
---

**Solution Summary:**

*   **Total Discount Achieved:** 52.50
*   **Total Points Used:** 95.00
*   **Final Amounts Spent per Method:**
    *   PUNKTY: 95.00
    *   BosBankrut: 200.00
    *   mZysk: 160.00
---

## Comparison with Task Description's Example

The task description provided an example solution with the following characteristics:

*   **Total Discount:** 45.00
*   **Total Points Used:** 100.00
*   **Final Amounts Spent per Method:**
    *   mZysk: 165.00
    *   BosBankrut: 190.00
    *   PUNKTY: 100.00

**Key Finding:** This backtracking algorithm found a solution that yields a **higher total discount (52.50)** compared to the example solution's discount (45.00).

## Rule Compliance Verification

The solution adheres to all specified payment and discount rules:

1.  **Payment Structure:** Orders are paid either fully by one traditional method, fully by points, or partially by points and partially by one traditional method.
    *   *This solution uses partial points + traditional for `ORDER1`, `ORDER2`, `ORDER3`, and full points for `ORDER4`, which is valid.*
2.  **Bank Card Discounts (Rule 2):** A bank-specific discount applies if the *entire* order is paid by that card and the card's promotion is listed for the order.
    *   *This solution does not utilize this specific rule, opting for points-based discounts which proved more beneficial overall.*
3.  **Partial Points Discount (Rule 3):** If at least 10% of the order value is paid with points, a 10% discount applies to the entire order.
    *   *This rule is applied to `ORDER1`, `ORDER2`, and `ORDER3`, correctly calculating the 10% discount on their respective total values.*
4.  **Full Points Discount (Rule 4):** If an entire order is paid with points, the "PUNKTY" method's specific discount (15%) is used.
    *   *This rule is applied to `ORDER4`, yielding a 7.50 (15% of 50.00) discount.*
5.  **Discount Exclusivity (Rule 5):** Applying a partial points discount excludes applying an additional bank card discount for that same order.
    *   *This is respected. For `ORDER1`, `ORDER2`, and `ORDER3`, the discount applied is solely the 10% partial points discount.*
6.  **Using Cards Not in Order Promotions:** A card not listed in an order's `promotions` can still be used to pay (partially or fully) but without its specific bank card discount.
    *   *This is relevant for `ORDER2` (paid partially with mZysk, not in its promotions) and `ORDER1` (paid partially with BosBankrut, not in its promotions). In both cases, the discount applied is the points-based discount, and the traditional cards are validly used for the remaining balance.*

## Conclusion

The backtracking algorithm successfully identified a payment strategy that:
*   Ensures all orders are fully paid.
*   Complies with all specified payment and discount rules.
*   Achieves a **higher total discount (52.50)** than the example solution (45.00), demonstrating its effectiveness in optimizing for maximum savings.

The algorithm prioritizes maximizing the total discount, and secondarily considers point usage if multiple strategies yield the same maximum discount, as per the problem's objectives.