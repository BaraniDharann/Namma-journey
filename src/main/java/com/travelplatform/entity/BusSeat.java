package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One seat or berth in a bus's layout (V30). Deck 0 is the lower deck, 1 the upper. A seater has no
 * berth; a sleeper is a SINGLE or DOUBLE berth. {@link #pairLabel} is the seat right next to this
 * one, which the gender rule looks at; pairs always point both ways.
 */
@Entity
@Table(name = "bus_seats")
@Data
@NoArgsConstructor
public class BusSeat {

    public enum Kind { SEATER, SLEEPER }

    public enum Berth { SINGLE, DOUBLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 8)
    private String label;

    @Column(nullable = false)
    private int deck;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "col_no", nullable = false)
    private int colNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private Berth berth;

    @Column(name = "pair_label", length = 8)
    private String pairLabel;

    @Column(name = "ladies_only", nullable = false)
    private boolean ladiesOnly;

    public BusSeatClass seatClass() {
        return BusSeatClass.of(kind, berth, deck);
    }
}
