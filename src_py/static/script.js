/*
 * 文件名: static/script.js
 * 版本: 最终完整版 (包含所有功能和优化)
 * 描述: 此脚本处理所有前端交互，包括光标跟随的动态粒子背景、API通信、UI渲染、表单处理和弹窗逻辑。
 * [已修复 V4] 修复了桌面版复选框点击无响应的“双重反转”Bug。
 */

document.addEventListener('DOMContentLoaded', () => {

    // --- ✨ 核心功能 1：动态粒子背景 (无改动) ✨ ---
    const particleCanvas = document.getElementById('particle-canvas');
    if (particleCanvas) {
        const ctx = particleCanvas.getContext('2d');
        let particles = [];
        let particleCount = 50;
        const maxDistance = 120;
        const mouse = { x: null, y: null };

        const setCanvasSize = () => {
            particleCanvas.width = window.innerWidth;
            particleCanvas.height = window.innerHeight;
            particleCount = Math.floor(particleCanvas.width / 30);
            if (particleCount > 100) particleCount = 100;
        };

        class Particle {
            constructor() {
                this.x = Math.random() * particleCanvas.width;
                this.y = Math.random() * particleCanvas.height;
                this.vx = Math.random() * 0.4 - 0.2;
                this.vy = Math.random() * 0.4 - 0.2;
                this.radius = Math.random() * 1.5 + 0.5;
            }
            update() {
                this.x += this.vx;
                this.y += this.vy;
                if (this.x < 0 || this.x > particleCanvas.width) this.vx *= -1;
                if (this.y < 0 || this.y > particleCanvas.height) this.vy *= -1;
            }
            draw() {
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
                ctx.fill();
            }
        }

        const initParticles = () => {
            particles = [];
            for (let i = 0; i < particleCount; i++) {
                particles.push(new Particle());
            }
        };

        const connectParticles = () => {
            for (let i = 0; i < particles.length; i++) {
                for (let j = i + 1; j < particles.length; j++) {
                    const dx = particles[i].x - particles[j].x;
                    const dy = particles[i].y - particles[j].y;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance < maxDistance) {
                        ctx.beginPath();
                        ctx.moveTo(particles[i].x, particles[i].y);
                        ctx.lineTo(particles[j].x, particles[j].y);
                        ctx.strokeStyle = `rgba(255, 255, 255, ${1 - distance / maxDistance * 0.5})`;
                        ctx.lineWidth = 0.5;
                        ctx.stroke();
                    }
                }
            }
            if (mouse.x && mouse.y) {
                particles.forEach(p => {
                    const dx = p.x - mouse.x;
                    const dy = p.y - mouse.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance < maxDistance * 1.5) {
                        ctx.beginPath();
                        ctx.moveTo(p.x, p.y);
                        ctx.lineTo(mouse.x, mouse.y);
                        ctx.strokeStyle = `rgba(255, 255, 255, ${1 - distance / (maxDistance * 1.5)})`;
                        ctx.lineWidth = 0.3;
                        ctx.stroke();
                    }
                });
            }
        };

        const animate = () => {
            ctx.clearRect(0, 0, particleCanvas.width, particleCanvas.height);
            particles.forEach(p => { p.update(); p.draw(); });
            connectParticles();
            requestAnimationFrame(animate);
        };

        setCanvasSize();
        initParticles();
        animate();
        window.addEventListener('resize', () => { setCanvasSize(); initParticles(); });
        window.addEventListener('mousemove', event => { mouse.x = event.clientX; mouse.y = event.clientY; });
        window.addEventListener('mouseout', () => { mouse.x = null; mouse.y = null; });
    }

    // --- 🛠️ 核心功能 2：工具函数 (无改动) ---
    const api = {
        async request(method, url, data) {
            const options = {
                method,
                headers: { 'Content-Type': 'application/json' }
            };
            if (data) {
                options.body = JSON.stringify(data);
            }
            try {
                const response = await fetch(url, options);
                if (!response.ok) {
                    const resData = await response.json().catch(() => ({}));
                    throw new Error(resData.message || `请求失败: ${response.status}`);
                }
                return await response.json().catch(() => ({ message: '操作成功' }));
            } catch (e) {
                throw new Error(e.message || '网络错误，请检查连接');
            }
        },
        get: (url) => api.request('GET', url, undefined),
        post: (url, data) => api.request('POST', url, data),
        put: (url, data) => api.request('PUT', url, data),
        delete: (url) => api.request('DELETE', url, undefined)
    };
    const showToast = (message, type = 'success') => {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast-notification ${type}`;
        toast.innerHTML = `<span>${message}</span>`;
        container.appendChild(toast);
        requestAnimationFrame(() => { toast.classList.add('show'); });
        setTimeout(() => {
            toast.classList.remove('show');
            toast.addEventListener('transitionend', () => toast.remove());
        }, 3000);
    };
    const toggleButtonLoading = (btn, isLoading) => {
        if (!btn) return;
        btn.disabled = isLoading;
        if (isLoading) {
            btn.dataset.originalContent = btn.innerHTML;
            btn.innerHTML = '<div class="spinner"></div>';
        } else if (btn.dataset.originalContent) {
            btn.innerHTML = btn.dataset.originalContent;
        }
    };
    let ALL_DATA = {};

    // --- 🎨 核心功能 3：UI 渲染 (无改动) ---
    const renderSubscriptions = (data) => {
        const subListEl = document.getElementById('sub-list');
        subListEl.innerHTML = '';
        if (!data.subscriptions || data.subscriptions.length === 0) {
            subListEl.innerHTML = `<div class="loading-state">无订阅，请在右侧添加~</div>`;
            return;
        }
        const enabled_ids = new Set(data.aggregation_enabled || []);
        data.subscriptions.forEach(sub => {
            const isEnabled = enabled_ids.has(sub.id);
            const item = document.createElement('div');
            item.className = 'sub-item';
            if (isEnabled) {
                item.classList.add('selected');
            }
            item.dataset.id = sub.id;
            item.innerHTML = `
                <div class="custom-checkbox-container">
                    <input type="checkbox" id="sub-check-${sub.id}" class="custom-checkbox-input enabled-sub" value="${sub.id}" ${isEnabled ? 'checked' : ''}>
                    <label for="sub-check-${sub.id}" class="custom-checkbox"></label>
                </div>
                <div class="sub-info">
                    <div class="sub-name">${sub.name}</div>
                    <div class="sub-url">${sub.url}</div>
                </div>
                <div class="sub-actions">
                    <button class="action-btn filter-btn" title="独立过滤"><i class="fa-solid fa-filter"></i></button>
                    <button class="action-btn edit-btn" title="编辑"><i class="fa-solid fa-pencil"></i></button>
                    <button class="action-btn delete-btn" title="删除"><i class="fa-solid fa-trash-can"></i></button>
                </div>
            `;
            subListEl.appendChild(item);
        });
        updateCheckAllState();
    };
    const renderGlobalFilter = (data) => {
        document.getElementById('globalFilterEnabled').checked = data.global_filter_enabled || false;
        document.getElementById('globalFilterKeywords').value = data.global_filter_keywords || '';
    };
    const updateCheckAllState = () => {
        const checkAll = document.getElementById('checkAll');
        if (!checkAll) return;
        const allChecks = document.querySelectorAll('.enabled-sub');
        if (allChecks.length === 0) {
            checkAll.checked = false;
            return;
        }
        const allChecked = Array.from(allChecks).every(c => c.checked);
        checkAll.checked = allChecked;
    };

    // --- 🔄 核心功能 4：数据加载与初始化 (无改动) ---
    const loadAndRender = async () => {
        try {
            const data = await api.get('/api/data');
            ALL_DATA = data;
            renderSubscriptions(data);
            renderGlobalFilter(data);
        } catch (error) {
            showToast(error.message, 'error');
            document.getElementById('sub-list').innerHTML = `<div class="loading-state">加载失败: ${error.message}</div>`;
        }
    };

    // --- 🪟 核心功能 5：弹窗 (Modals) 管理 (无改动) ---
    const modalBackdrop = document.getElementById('modal-backdrop');
    const allModals = document.querySelectorAll('.modal');
    const openModal = (modalId, sub) => {
        const modal = document.getElementById(modalId);
        if (!modal) return;
        if (modalId === 'editModal' && sub) {
            modal.querySelector('#editSubId').value = sub.id;
            modal.querySelector('#editSubName').value = sub.name;
            modal.querySelector('#editSubUrl').value = sub.url;
        } else if (modalId === 'filterModal' && sub) {
            modal.querySelector('#filterSubId').value = sub.id;
            modal.querySelector('#filterKeywords').value = sub.filter_keywords ? sub.filter_keywords.replace(/,/g, '\n') : '';
            modal.querySelector('#singleFilterEnabled').checked = sub.filter_enabled || false;
            modal.querySelector('#filterModalTitle').textContent = `为 “${sub.name}” 设置独立过滤器`;
        }
        modalBackdrop.style.visibility = 'visible';
        modalBackdrop.style.opacity = '1';
        modal.style.visibility = 'visible';
        modal.style.opacity = '1';
        modal.style.transform = 'translate(-50%, -50%) scale(1)';
    };
    const closeModal = () => {
        modalBackdrop.style.opacity = '0';
        allModals.forEach(modal => {
            modal.style.opacity = '0';
            modal.style.transform = 'translate(-50%, -45%) scale(0.95)';
        });
        setTimeout(() => {
            modalBackdrop.style.visibility = 'hidden';
            allModals.forEach(modal => modal.style.visibility = 'hidden');
        }, 300);
    };

    // --- 🖱️ 核心功能 6：事件监听与处理 ---
    
    // [辅助函数] (无改动)
    const handleApiButtonClick = async (e, apiCall) => {
        const btn = e.currentTarget;
        toggleButtonLoading(btn, true);
        try {
            const result = await apiCall();
            showToast(result.message || '操作成功', 'success');
            await loadAndRender();
            return true;
        } catch (error) {
            showToast(error.message, 'error');
            return false;
        } finally {
            toggleButtonLoading(btn, false);
        }
    };
    
    // [表单提交] (无改动)
    document.getElementById('add-form').addEventListener('submit', async function(e) {
        e.preventDefault();
        const btn = this.querySelector('button[type="submit"]');
        const name = this.querySelector('#subName').value.trim();
        const url = this.querySelector('#subUrl').value.trim();
        if (!name || !url) return showToast('名称和地址不能为空', 'error');
        const success = await handleApiButtonClick({ currentTarget: btn }, () => api.post('/api/subscriptions', { name, url }));
        if (success) {
            this.reset();
        }
    });

    // --- [关键修改] 重构订阅列表的事件监听器以修复Bug ---
    
    // 1. 新增一个专门处理状态变更后UI更新的 'change' 事件监听器
    document.getElementById('sub-list').addEventListener('change', e => {
        // 当任何复选框的状态真实发生改变时...
        if (e.target.matches('.enabled-sub')) {
            const subItem = e.target.closest('.sub-item');
            if (subItem) {
                // 根据复选框的最新状态，切换整行的高亮class
                subItem.classList.toggle('selected', e.target.checked);
            }
            // 并且更新“聚合全部”复选框的状态
            updateCheckAllState();
        }
    });
    
    // 2. 修改 'click' 事件监听器，使其只处理意图，不直接操作UI
    document.getElementById('sub-list').addEventListener('click', async e => {
        const subItem = e.target.closest('.sub-item');
        if (!subItem) return;

        const subId = subItem.dataset.id;
        const sub = ALL_DATA.subscriptions.find(s => s.id === subId);
        if (!sub) return;

        // 检查点击的是否是功能按钮
        if (e.target.closest('.sub-actions')) {
            if (e.target.closest('.edit-btn')) {
                openModal('editModal', sub);
            } else if (e.target.closest('.filter-btn')) {
                openModal('filterModal', sub);
            } else if (e.target.closest('.delete-btn')) {
                if (confirm(`确定要删除订阅 "${sub.name}" 吗？`)) {
                    await handleApiButtonClick({ currentTarget: e.target.closest('.action-btn') }, () => api.delete(`/api/subscriptions/${subId}`));
                }
            }
        } 
        // 如果点击的不是功能按钮，那么意图就是“选择/取消选择”
        else {
            const checkbox = subItem.querySelector('.custom-checkbox-input');
            // 如果我们点击的不是复选框本身，就手动触发它的点击事件。
            // 这样可以利用浏览器的原生行为来改变状态并触发上面的 'change' 事件，避免双重反转。
            if (checkbox && e.target !== checkbox) {
                 checkbox.click();
            }
        }
    });
    // --- [修改结束] ---
    
    // [其他按钮监听] (无改动)
    document.getElementById('saveAggregationBtn').addEventListener('click', e => {
        const enabled_ids = Array.from(document.querySelectorAll('.enabled-sub:checked')).map(cb => cb.value);
        handleApiButtonClick(e, () => api.post('/api/aggregation', { enabled_ids }));
    });
    document.getElementById('checkAll').addEventListener('change', e => {
        const isChecked = e.target.checked;
        document.querySelectorAll('.enabled-sub').forEach(cb => {
            // 只在需要改变状态时才改变，避免不必要的事件触发
            if (cb.checked !== isChecked) {
                cb.click();
            }
        });
    });
    document.getElementById('saveGlobalFilterBtn').addEventListener('click', async e => {
        const enabled = document.getElementById('globalFilterEnabled').checked;
        const keywords = document.getElementById('globalFilterKeywords').value.trim();
        await handleApiButtonClick(e, () => api.post('/api/filters/global', { enabled, keywords }));
    });
    document.getElementById('saveEditBtn').addEventListener('click', async e => {
        const id = document.getElementById('editSubId').value;
        const name = document.getElementById('editSubName').value.trim();
        const url = document.getElementById('editSubUrl').value.trim();
        if (!name || !url) return showToast('名称和地址不能为空', 'error');
        const success = await handleApiButtonClick(e, () => api.put(`/api/subscriptions/${id}`, { name, url }));
        if (success) closeModal();
    });
    document.getElementById('saveFilterBtn').addEventListener('click', async e => {
        const id = document.getElementById('filterSubId').value;
        const keywords = document.getElementById('filterKeywords').value.trim().split('\n').map(k => k.trim()).filter(Boolean).join(',');
        const enabled = document.getElementById('singleFilterEnabled').checked;
        const success = await handleApiButtonClick(e, () => api.put(`/api/subscriptions/${id}`, { filter_keywords: keywords, filter_enabled: enabled }));
        if (success) closeModal();
    });
    function copyTextToClipboard(text) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(text);
        } else {
            return new Promise((resolve, reject) => {
                const textArea = document.createElement("textarea");
                textArea.value = text;
                textArea.style.position = 'fixed';
                textArea.style.top = '-9999px';
                textArea.style.left = '-9999px';
                document.body.appendChild(textArea);
                textArea.focus();
                textArea.select();
                try {
                    const successful = document.execCommand('copy');
                    document.body.removeChild(textArea);
                    if (successful) {
                        resolve();
                    } else {
                        reject(new Error('无法使用后备方案复制文本'));
                    }
                } catch (err) {
                    document.body.removeChild(textArea);
                    reject(err);
                }
            });
        }
    }
    document.getElementById('copyBtn').addEventListener('click', function() {
        copyTextToClipboard(document.getElementById('aggregatedUrl').value).then(() => {
            showToast('聚合链接已复制!', 'success');
            const iconCopy = this.querySelector('.icon-copy');
            const iconCheck = this.querySelector('.icon-check');
            iconCopy.style.display = 'none';
            iconCheck.style.display = 'inline';
            setTimeout(() => {
                iconCopy.style.display = 'inline';
                iconCheck.style.display = 'none';
            }, 2000);
        }).catch(err => showToast('复制失败: ' + err.message, 'error'));
    });
    modalBackdrop.addEventListener('click', closeModal);
    document.querySelectorAll('.modal-close-btn').forEach(btn => btn.addEventListener('click', closeModal));

    // --- 🚀 应用程序启动 ---
    loadAndRender();
});
