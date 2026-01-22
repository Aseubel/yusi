package model

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"
)

// JSONMapStringString handles Map<String, String> <-> JSON
type JSONMapStringString map[string]string

func (m JSONMapStringString) Value() (driver.Value, error) {
	if m == nil {
		return "{}", nil
	}
	return json.Marshal(m)
}

func (m *JSONMapStringString) Scan(value interface{}) error {
	if value == nil {
		*m = make(map[string]string)
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion to []byte failed")
	}
	return json.Unmarshal(bytes, m)
}

// JSONMapStringBool handles Map<String, Boolean> <-> JSON
type JSONMapStringBool map[string]bool

func (m JSONMapStringBool) Value() (driver.Value, error) {
	if m == nil {
		return "{}", nil
	}
	return json.Marshal(m)
}

func (m *JSONMapStringBool) Scan(value interface{}) error {
	if value == nil {
		*m = make(map[string]bool)
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion to []byte failed")
	}
	return json.Unmarshal(bytes, m)
}

// JSONSetString handles Set<String> <-> JSON (stored as list)
type JSONSetString []string

func (s JSONSetString) Value() (driver.Value, error) {
	if s == nil {
		return "[]", nil
	}
	return json.Marshal(s)
}

func (s *JSONSetString) Scan(value interface{}) error {
	if value == nil {
		*s = make([]string, 0)
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion to []byte failed")
	}
	return json.Unmarshal(bytes, s)
}

// SituationReport (Embedded struct or JSON)
type SituationReport struct {
	ScenarioID        string             `json:"scenarioId"`
	Personal          []PersonalSketch   `json:"personal"`
	Pairs             []PairCompatibility `json:"pairs"`
	PublicSubmissions []PublicSubmission `json:"publicSubmissions"`
}

type PersonalSketch struct {
	UserID string `json:"userId"`
	Sketch string `json:"sketch"`
}

type PairCompatibility struct {
	UserA  string `json:"userA"`
	UserB  string `json:"userB"`
	Score  int    `json:"score"`
	Reason string `json:"reason"`
}

type PublicSubmission struct {
	UserID  string `json:"userId"`
	Content string `json:"content"`
}

// JSONSituationReport handles SituationReport <-> JSON
type JSONSituationReport SituationReport

func (r JSONSituationReport) Value() (driver.Value, error) {
	return json.Marshal(r)
}

func (r *JSONSituationReport) Scan(value interface{}) error {
	if value == nil {
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion to []byte failed")
	}
	return json.Unmarshal(bytes, r)
}

type SituationRoom struct {
	Code                 string               `gorm:"primaryKey;size:32" json:"code"`
	Status               string               `gorm:"size:20" json:"status"`
	OwnerID              string               `gorm:"column:owner_id;size:64" json:"ownerId"`
	ScenarioID           string               `gorm:"column:scenario_id;size:32" json:"scenarioId"`
	Members              JSONSetString        `gorm:"type:text" json:"members"`
	Submissions          JSONMapStringString  `gorm:"type:text" json:"submissions"`
	SubmissionVisibility JSONMapStringBool    `gorm:"type:text" json:"submissionVisibility"`
	CancelVotes          JSONSetString        `gorm:"type:text" json:"cancelVotes"`
	CreatedAt            time.Time            `gorm:"column:created_at" json:"createdAt"`
	Report               *JSONSituationReport `gorm:"type:text" json:"report"`
	
	// Transient fields populated manually
	MemberNames map[string]string `gorm:"-" json:"memberNames,omitempty"`
	Scenario    *SituationScenario `gorm:"-" json:"scenario,omitempty"`
}

func (SituationRoom) TableName() string {
	return "situation_room"
}

type SituationScenario struct {
	ID           string `gorm:"primaryKey;size:32" json:"id"`
	Title        string `gorm:"size:100" json:"title"`
	Description  string `gorm:"type:text" json:"description"`
	SubmitterID  string `gorm:"column:submitter_id;size:64" json:"submitterId"`
	RejectReason string `gorm:"column:reject_reason;type:text" json:"rejectReason"`
	Status       int    `gorm:"default:0" json:"status"`
}

func (SituationScenario) TableName() string {
	return "situation_scenario"
}
