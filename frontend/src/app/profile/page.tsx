'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import toast from 'react-hot-toast';

/**
 * Profile Page - View and edit user profile
 */

export default function ProfilePage() {
  const { user, loading: authLoading, refreshUser, updateUser } = useAuth();
  const router = useRouter();
  
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  
  // Form state
  const [name, setName] = useState('');
  const [bio, setBio] = useState('');
  const [skills, setSkills] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');

  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/login');
    }
  }, [user, authLoading, router]);

  useEffect(() => {
    if (user) {
      setName(user.name || '');
      setBio(user.bio || '');
      setSkills(user.skills || []);
    }
  }, [user]);

  const handleAddSkill = () => {
    if (skillInput.trim() && !skills.includes(skillInput.trim())) {
      setSkills([...skills, skillInput.trim()]);
      setSkillInput('');
    }
  };

  const handleRemoveSkill = (skillToRemove: string) => {
    setSkills(skills.filter(s => s !== skillToRemove));
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      const res = await axios.put('/api/users/profile', {
        name,
        bio,
        skills
      });
      
      if (res.data.success) {
        toast.success('Profile updated!');
        setEditing(false);
        // Update user in context
        updateUser({ name, bio, skills });
        // Also refresh from server
        refreshUser();
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error updating profile');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    // Reset to original values
    if (user) {
      setName(user.name || '');
      setBio(user.bio || '');
      setSkills(user.skills || []);
    }
    setEditing(false);
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Loading...</div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold mb-8">My Profile</h1>

        <div className="card">
          {/* Avatar & Basic Info */}
          <div className="flex items-center mb-6 pb-6 border-b">
            <div className="w-20 h-20 bg-primary-100 rounded-full flex items-center justify-center text-3xl font-bold text-primary-600">
              {user.name?.charAt(0).toUpperCase() || '?'}
            </div>
            <div className="ml-6">
              <h2 className="text-2xl font-semibold">{user.name}</h2>
              <p className="text-gray-600">{user.email}</p>
              <span className="inline-block mt-2 px-3 py-1 bg-primary-100 text-primary-700 rounded-full text-sm font-medium">
                💰 {user.credits} credits
              </span>
            </div>
          </div>

          {/* Profile Form */}
          <div className="space-y-6">
            {/* Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Name
              </label>
              {editing ? (
                <input
                  type="text"
                  className="input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              ) : (
                <p className="text-gray-900">{user.name}</p>
              )}
            </div>

            {/* Bio */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Bio
              </label>
              {editing ? (
                <textarea
                  className="input min-h-[100px]"
                  placeholder="Tell us about yourself..."
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                />
              ) : (
                <p className="text-gray-900">{user.bio || 'No bio added yet.'}</p>
              )}
            </div>

            {/* Skills */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Skills
              </label>
              {editing ? (
                <div>
                  <div className="flex gap-2 mb-2">
                    <input
                      type="text"
                      className="input flex-1"
                      placeholder="Add a skill..."
                      value={skillInput}
                      onChange={(e) => setSkillInput(e.target.value)}
                      onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddSkill())}
                    />
                    <button
                      type="button"
                      onClick={handleAddSkill}
                      className="btn-primary px-4"
                    >
                      Add
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {skills.map((skill, idx) => (
                      <span
                        key={idx}
                        className="px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm flex items-center gap-2"
                      >
                        {skill}
                        <button
                          onClick={() => handleRemoveSkill(skill)}
                          className="text-gray-500 hover:text-red-500"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {(user.skills || []).length > 0 ? (
                    user.skills.map((skill: string, idx: number) => (
                      <span
                        key={idx}
                        className="px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm"
                      >
                        {skill}
                      </span>
                    ))
                  ) : (
                    <p className="text-gray-500">No skills added yet.</p>
                  )}
                </div>
              )}
            </div>

            {/* Action Buttons */}
            <div className="pt-6 border-t">
              {editing ? (
                <div className="flex gap-4">
                  <button
                    onClick={handleSave}
                    disabled={saving}
                    className="btn-primary flex-1"
                  >
                    {saving ? 'Saving...' : 'Save Changes'}
                  </button>
                  <button
                    onClick={handleCancel}
                    disabled={saving}
                    className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setEditing(true)}
                  className="btn-primary"
                >
                  Edit Profile
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Account Info */}
        <div className="card mt-6">
          <h3 className="font-semibold mb-4">Account Information</h3>
          <div className="space-y-3 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Email</span>
              <span>{user.email}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Member since</span>
              <span>{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'N/A'}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
