package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"log/slog"
)

// Decryptor provides content decryption capabilities
// Compatible with Java's AttributeEncryptor using AES/GCM/NoPadding
type Decryptor struct {
	key     []byte
	useMock bool
}

// NewDecryptor creates a new Decryptor instance
// The key should be at least 16 characters (128-bit AES key as used in Java)
// If the key is empty or invalid, it falls back to mock mode
func NewDecryptor(key string) *Decryptor {
	d := &Decryptor{
		key:     nil,
		useMock: true,
	}

	// Java uses first 16 characters of the key for AES-128
	if len(key) >= 16 {
		d.key = []byte(key[:16])
		d.useMock = false
		slog.Info("Decryptor initialized with valid key",
			"key_length", 16,
		)
	} else if len(key) > 0 {
		slog.Warn("Invalid encryption key length, using mock decryption",
			"expected", ">=16",
			"got", len(key),
		)
	} else {
		slog.Warn("No encryption key provided, using mock decryption")
	}

	return d
}

// Decrypt decrypts the given encrypted content
// Falls back to mock decryption if real decryption is not configured
func (d *Decryptor) Decrypt(encryptedContent string) (string, error) {
	if encryptedContent == "" {
		return "", nil
	}

	if d.useMock {
		return d.mockDecrypt(encryptedContent)
	}
	return d.realDecrypt(encryptedContent)
}

// mockDecrypt is a placeholder for real decryption logic
// Returns the content as-is (assumes it's either plaintext or returns ciphertext)
func (d *Decryptor) mockDecrypt(encryptedContent string) (string, error) {
	slog.Debug("Using mock decryption for content",
		"content_length", len(encryptedContent),
	)

	// Try to check if it looks like base64 encoded data
	// If decoding fails, it's probably plaintext
	_, err := base64.StdEncoding.DecodeString(encryptedContent)
	if err != nil {
		// Content is probably plaintext
		return encryptedContent, nil
	}

	// Return as-is in mock mode - we can't decrypt without the key
	slog.Warn("Content appears to be encrypted but no valid key available")
	return "[ENCRYPTED_CONTENT]", nil
}

// realDecrypt performs AES-128-GCM decryption compatible with Java's AttributeEncryptor
// Format: base64(ivLength(4 bytes) + iv + ciphertext + tag)
// The Java implementation uses:
// - AES/GCM/NoPadding
// - 12-byte IV (recommended for GCM)
// - 128-bit authentication tag
func (d *Decryptor) realDecrypt(encryptedContent string) (string, error) {
	// Decode base64
	cipherData, err := base64.StdEncoding.DecodeString(encryptedContent)
	if err != nil {
		slog.Warn("Failed to decode base64 content, treating as plaintext",
			"error", err,
		)
		// Might be plaintext (not encrypted)
		return encryptedContent, nil
	}

	// Check minimum length: 4 (ivLength) + at least 12 (iv) + 16 (min ciphertext with tag)
	if len(cipherData) < 32 {
		slog.Warn("Encrypted data too short, treating as plaintext",
			"length", len(cipherData),
		)
		return encryptedContent, nil
	}

	// Parse Java's format: ivLength(4 bytes big-endian) + iv + ciphertext
	ivLen := int(binary.BigEndian.Uint32(cipherData[:4]))

	// Validate IV length (Java uses 12 bytes for GCM)
	if ivLen <= 0 || ivLen > 16 || 4+ivLen > len(cipherData) {
		slog.Warn("Invalid IV length in encrypted data, treating as plaintext",
			"iv_length", ivLen,
		)
		return encryptedContent, nil
	}

	iv := cipherData[4 : 4+ivLen]
	ciphertext := cipherData[4+ivLen:]

	// Create AES cipher
	block, err := aes.NewCipher(d.key)
	if err != nil {
		slog.Error("Failed to create AES cipher",
			"error", err,
		)
		return "", errors.New("failed to initialize cipher")
	}

	// Create GCM mode with 128-bit tag (standard)
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		slog.Error("Failed to create GCM mode",
			"error", err,
		)
		return "", errors.New("failed to initialize GCM")
	}

	// Decrypt
	// Note: In GCM, the ciphertext includes the authentication tag at the end
	plaintext, err := gcm.Open(nil, iv, ciphertext, nil)
	if err != nil {
		slog.Warn("Failed to decrypt content, might be plaintext or wrong key",
			"error", err,
		)
		// Return original content if decryption fails (might be plaintext)
		return encryptedContent, nil
	}

	return string(plaintext), nil
}

// IsMockMode returns whether the decryptor is using mock mode
func (d *Decryptor) IsMockMode() bool {
	return d.useMock
}
